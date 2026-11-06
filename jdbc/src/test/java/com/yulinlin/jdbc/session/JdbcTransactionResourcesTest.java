package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.exception.OrmException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JdbcTransactionResourcesTest {
    @Test void mysqlStatementFailureCanBeCaughtAndCommitted() {
        var jdbc = new RecordingJdbc("MySQL");
        var resources = new JdbcTransactionResources(jdbc).initialize();
        Connection connection = resources.getConnection();
        var batch = new BatchUpdateException("duplicate", new int[]{1, Statement.EXECUTE_FAILED});
        batch.setNextException(new SQLException("duplicate detail", "23000", 1062));
        resources.executionFailed(connection, new IllegalStateException("wrapper", batch));
        resources.releaseConnection(connection);
        assertFalse(resources.isRollbackOnly());
        assertSame(connection, resources.getConnection());
        resources.releaseConnection(connection);
        resources.finish(false);
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertTrue(jdbc.closed);
    }

    static Stream<Arguments> fatalErrors() {
        var batch = new BatchUpdateException("batch", new int[]{Statement.EXECUTE_FAILED});
        batch.setNextException(new SQLException("deadlock", "40001", 1213));
        var suppressed = new SQLException("duplicate", "23000");
        suppressed.addSuppressed(new SQLException("connection lost during close", "08006"));
        return Stream.of(
                Arguments.of("PostgreSQL", new SQLException("duplicate", "23505")),
                Arguments.of("PostgreSQL", new SQLException("syntax", "42601")),
                Arguments.of("MySQL", new SQLException("deadlock", "40001", 1213)),
                Arguments.of("MySQL", new SQLException("lock timeout", "HY000", 1205)),
                Arguments.of("MariaDB", new SQLException("lock timeout", "HY000", 1205)),
                Arguments.of("H2", new SQLException("invalid transaction", "25000")),
                Arguments.of("H2", new SQLException("connection", "08006")),
                Arguments.of("H2", new SQLNonTransientConnectionException("disconnected")),
                Arguments.of("H2", new SQLTransientConnectionException("disconnected")),
                Arguments.of("Oracle", new SQLRecoverableException("connection must be replaced")),
                Arguments.of("MySQL", new SQLTimeoutException("timeout")),
                Arguments.of("SQLite", new SQLException("unknown transaction state")),
                Arguments.of("MySQL", batch), Arguments.of("MySQL", suppressed),
                Arguments.of("H2", new AssertionError("fatal JVM error")));
    }

    @ParameterizedTest @MethodSource("fatalErrors")
    void fatalErrorsPreventFurtherExecutionAndNeverCommit(String product, Throwable error) {
        var jdbc = new RecordingJdbc(product);
        var resources = new JdbcTransactionResources(jdbc).initialize();
        var connection = resources.getConnection();
        resources.executionFailed(connection, error);
        resources.releaseConnection(connection);
        assertTrue(resources.isRollbackOnly());
        assertThrows(OrmException.class, resources::getConnection);
        assertThrows(OrmException.class, () -> resources.finish(false));
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
        assertTrue(jdbc.closed);
    }

    @Test void stateCheckFailureKeepsOriginalErrorAndPreventsCommit() {
        var jdbc = new RecordingJdbc("H2");
        jdbc.metadataFailure = new SQLException("metadata unavailable", "08006");
        var resources = new JdbcTransactionResources(jdbc).initialize();
        var connection = resources.getConnection();
        var original = new SQLException("duplicate", "23505");
        resources.executionFailed(connection, original);
        resources.releaseConnection(connection);
        assertArrayEquals(new Throwable[]{jdbc.metadataFailure}, original.getSuppressed());
        resources.finish(true);
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
    }

    @Test void ordinaryErrorDoesNotClearAnExistingRollbackMark() {
        var jdbc = new RecordingJdbc("H2");
        var resources = new JdbcTransactionResources(jdbc).initialize();
        var connection = resources.getConnection();
        resources.setRollbackOnly();
        resources.executionFailed(connection, new SQLException("duplicate", "23505"));
        resources.releaseConnection(connection);
        assertTrue(resources.isRollbackOnly());
        resources.finish(true);
        assertEquals(0, jdbc.commits);
    }

    @Test void closedConnectionIsNotReplacedWithANewTransaction() {
        var jdbc = new RecordingJdbc("H2");
        var resources = new JdbcTransactionResources(jdbc).initialize();
        jdbc.closed = true;
        assertThrows(OrmException.class, resources::getConnection);
        assertTrue(resources.isRollbackOnly());
        resources.finish(true);
        assertEquals(1, jdbc.acquisitions);
        assertEquals(0, jdbc.commits);
    }

    @Test void postgresStatementFailureIsRecoveredOnlyByItsSavepoint() {
        var jdbc = new RecordingJdbc("PostgreSQL");
        var resources = new JdbcTransactionResources(jdbc);
        Object parent = resources.createSavepoint();
        Object child = resources.createSavepoint();
        Connection connection = resources.getConnection();
        resources.executionFailed(connection, new SQLException("duplicate", "23505"));
        resources.releaseConnection(connection);
        assertTrue(resources.isRollbackOnly());
        assertThrows(OrmException.class, resources::getConnection);
        resources.rollbackToSavepoint(child);
        assertFalse(resources.isRollbackOnly());
        assertSame(connection, resources.getConnection());
        resources.releaseConnection(connection);
        resources.releaseSavepoint(parent);
        resources.finish(false);
        assertEquals(1, jdbc.savepointRollbacks);
        assertEquals(0, jdbc.rollbacks);
        assertEquals(1, jdbc.commits);
        assertEquals(1, jdbc.acquisitions);
    }

    @Test void fatalFailureCannotBeRecoveredBySavepoint() {
        var jdbc = new RecordingJdbc("MySQL");
        var resources = new JdbcTransactionResources(jdbc);
        Object point = resources.createSavepoint();
        Connection connection = resources.getConnection();
        resources.executionFailed(connection, new SQLException("deadlock", "40001"));
        resources.releaseConnection(connection);
        assertThrows(OrmException.class, () -> resources.rollbackToSavepoint(point));
        assertTrue(resources.isRollbackOnly());
        resources.finish(true);
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
    }

    @Test void unsupportedSavepointsDoNotSilentlyJoinOuterTransaction() {
        var jdbc = new RecordingJdbc("H2");
        jdbc.savepointsSupported = false;
        var resources = new JdbcTransactionResources(jdbc);
        assertThrows(OrmException.class, resources::createSavepoint);
        assertFalse(resources.isRollbackOnly());
        resources.finish(true);
        assertTrue(jdbc.closed);
    }

    @Test void ownerThreadAndSavepointOrderAreEnforced() throws Exception {
        var jdbc = new RecordingJdbc("H2");
        var resources = new JdbcTransactionResources(jdbc);
        Object first = resources.createSavepoint();
        Object second = resources.createSavepoint();
        assertThrows(OrmException.class, () -> resources.releaseSavepoint(first));
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            worker.submit(() -> assertThrows(OrmException.class, resources::getConnection)).get();
        }
        resources.rollbackToSavepoint(second);
        resources.rollbackToSavepoint(first);
        resources.finish(false);
        assertEquals(1, jdbc.commits);
    }

    private static final class RecordingJdbc extends AbstractDataSource {
        final String product;
        boolean autoCommit = true, closed;
        int commits, rollbacks, acquisitions;
        int savepoints, savepointRollbacks;
        boolean savepointsSupported = true;
        SQLException metadataFailure;
        RecordingJdbc(String product) { this.product = product; }
        @Override public Connection getConnection() {
            acquisitions++;
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> { autoCommit = (boolean) args[0]; yield null; }
                        case "isClosed" -> closed;
                        case "commit" -> { commits++; yield null; }
                        case "rollback" -> { if (args == null || args.length == 0) rollbacks++; else savepointRollbacks++; yield null; }
                        case "setSavepoint" -> {
                            int id = ++savepoints;
                            yield new Savepoint() {
                                @Override public int getSavepointId() { return id; }
                                @Override public String getSavepointName() { return "point" + id; }
                            };
                        }
                        case "releaseSavepoint" -> null;
                        case "close" -> { closed = true; yield null; }
                        case "getMetaData" -> {
                            if (metadataFailure != null) throw metadataFailure;
                            yield Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(), new Class[]{DatabaseMetaData.class},
                                    (p, m, a) -> {
                                        if (m.getName().equals("getDatabaseProductName")) return product;
                                        if (m.getName().equals("supportsSavepoints")) return savepointsSupported;
                                        throw new AssertionError(m.getName());
                                    });
                        }
                        default -> throw new AssertionError(method.getName());
                    });
        }
        @Override public Connection getConnection(String username, String password) { return getConnection(); }
    }
}

package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.JdbcSessionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Lightweight recording JDBC driver: verifies batching/grouping, not database throughput. */
class JdbcBatchExecutionTest {
    private final List<RecordingSession> sessions = new ArrayList<>();

    @AfterEach void closeExecutors() {
        sessions.forEach(session -> session.getThreadPoolExecutor().shutdownNow());
    }

    @Test void hundredThousandRowsUseFourGroupsAndReuseEachConnectionAndStatement() throws Exception {
        Database db = new Database();
        db.firstBatches = new CountDownLatch(4); // Force all workers to hold distinct connections.
        RecordingSession session = session(db, 100_000);
        assertThat(session.supportsParallelWrites()).isTrue();
        assertThat(db.connections).isEmpty(); // Capability inspection must not open a connection.
        assertThat(session.run(true)).isEqualTo(100_000);
        assertThat(session.groupSizes).containsExactlyInAnyOrder(25_000, 25_000, 25_000, 25_000);
        assertThat(db.connections).hasSize(4);
        for (ConnectionState connection : db.connections) {
            assertThat(connection.statements).hasSize(1);
            BatchState statement = connection.statements.getFirst();
            assertThat(statement.batchSizes).hasSize(98);
            assertThat(statement.batchSizes.subList(0, 97)).containsOnly(256);
            assertThat(statement.batchSizes.getLast()).isEqualTo(168);
            assertThat(statement.boundParameters).isEqualTo(25_000);
            assertThat(statement.clearBatchCalls).isEqualTo(98);
            assertThat(statement.closed).isTrue();
            assertThat(connection.commits).isEqualTo(1);
            assertThat(connection.rollbacks).isZero();
            assertThat(connection.closed).isTrue();
        }
    }

    @Test void customBatchSizeFlushesFullBatchesAndTailWithoutIntermediateCommits() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 7);
        session.setParallelConnections(1);
        session.setExecuteBatchSize(3);
        assertThat(session.supportsParallelWrites()).isFalse();
        session.startTransaction();
        try {
            assertThat(session.run(true)).isEqualTo(7);
            assertThat(session.groupSizes).containsExactly(7);
            ConnectionState connection = db.connections.getFirst();
            assertThat(connection.statements.getFirst().batchSizes).containsExactly(3, 3, 1);
            assertThat(connection.commits).isZero();
            session.commitTransaction();
            assertThat(connection.commits).isEqualTo(1);
        } finally { session.rollbackTransaction(); }
    }

    @Test void exactMultipleDoesNotFlushEmptyTailAndNonParallelRequestIsNotSplit() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 512);
        assertThat(session.run(false)).isEqualTo(512);
        assertThat(session.groupSizes).containsExactly(512);
        assertThat(db.connections).hasSize(1);
        assertThat(db.connections.getFirst().statements.getFirst().batchSizes).containsExactly(256, 256);
        assertThat(session.workerThreads).containsExactly(Thread.currentThread().getName());
    }

    @Test void parallelThresholdAppliesToWholeRequestRatherThanSmallWorkerShares() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 129);
        ExecuteRequest<Object> request = ExecuteRequest.ofInsert(Object.class);
        request.setBatch(true); // Keep default 128, even though every worker share is below 128.
        assertThat(session.insert(request)).isEqualTo(129);
        assertThat(session.groupSizes).containsExactlyInAnyOrder(33, 32, 32, 32);
        assertThat(session.workerThreads).doesNotContain(Thread.currentThread().getName());
        assertThat(session.rows).hasSize(129);
    }

    @Test void belowThresholdRunsAsOneGroupOnOwnerThread() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 127);
        ExecuteRequest<Object> request = ExecuteRequest.ofInsert(Object.class);
        request.setBatch(true);
        assertThat(session.insert(request)).isEqualTo(127);
        assertThat(session.groupSizes).containsExactly(127);
        assertThat(session.workerThreads).containsExactly(Thread.currentThread().getName());
    }

    @Test void unsupportedSessionDoesNotEvenInvokeParallelGrouping() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 1_025);
        session.setParallelConnections(1);
        assertThat(session.run(true)).isEqualTo(1_025);
        assertThat(session.groupingCalls).isZero();
        assertThat(session.groupSizes).containsExactly(1_025);
        assertThat(db.connections).hasSize(1);
        assertThat(db.connections.getFirst().statements.getFirst().batchSizes).containsExactly(256, 256, 256, 256, 1);
        assertThat(session.workerThreads).containsExactly(Thread.currentThread().getName());
    }

    @Test void springBoundTransactionHasOneUnsplitGroupOnOwnerThread() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 1_025);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(db.source));
        transaction.executeWithoutResult(status -> {
            assertThat(session.supportsParallelWrites()).isFalse();
            assertThat(session.run(true)).isEqualTo(1_025);
            assertThat(session.groupingCalls).isZero();
            assertThat(db.connections.getFirst().commits).isZero();
        });
        assertThat(session.supportsParallelWrites()).isTrue();
        assertThat(session.groupSizes).containsExactly(1_025);
        assertThat(session.workerThreads).containsExactly(Thread.currentThread().getName());
        assertThat(db.connections.getFirst().commits).isEqualTo(1);
        assertThat(db.connections.getFirst().closed).isTrue();
    }

    @Test void lateBatchFailureRollsBackEarlierFlushesAndClosesStatementAndConnection() throws Exception {
        Database db = new Database(); db.failOnBatch = 2;
        RecordingSession session = session(db, 600); session.setParallelConnections(1);
        assertThatThrownBy(() -> session.run(true)).isInstanceOf(BatchUpdateException.class);
        ConnectionState connection = db.connections.getFirst();
        assertThat(connection.statements.getFirst().batchSizes).containsExactly(256, 256);
        assertThat(connection.statements.getFirst().closed).isTrue();
        assertThat(connection.commits).isZero();
        assertThat(connection.rollbacks).isEqualTo(1);
        assertThat(connection.closed).isTrue();
        assertThat(session.isOpenTransaction()).isFalse();
    }

    @Test void successWithoutCountIsNonNegativeAndExplicitFailureTriggersRollback() throws Exception {
        Database success = new Database(); success.updateCount = Statement.SUCCESS_NO_INFO;
        assertThat(session(success, 7).run(false)).isEqualTo(7);
        Database failure = new Database(); failure.updateCount = Statement.EXECUTE_FAILED;
        assertThatThrownBy(() -> session(failure, 7).run(false)).isInstanceOf(BatchUpdateException.class);
        assertThat(failure.connections.getFirst().commits).isZero();
        assertThat(failure.connections.getFirst().rollbacks).isEqualTo(1);
    }

    @Test void emptyInputDoesNotAcquireConnectionAndSqlWithoutParametersIsAccepted() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 0);
        assertThat(session.run(true)).isZero();
        assertThat(db.connections).isEmpty();
        session.rows = List.of(new ParseResult(null, new SqlNode("delete from t"), null));
        assertThat(session.run(false)).isEqualTo(1);
        assertThat(db.connections.getFirst().statements.getFirst().boundParameters).isZero();
    }

    @Test void eachSqlTemplateReusesOneStatementAndFlushesItsOwnTail() throws Exception {
        Database db = new Database(); RecordingSession session = session(db, 0);
        session.setExecuteBatchSize(3);
        List<ParseResult> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(new ParseResult(null, parameterNode("insert into t values (?)"), null));
            rows.add(new ParseResult(null, parameterNode("update t set v = ?"), null));
        }
        session.rows = rows;
        assertThat(session.run(false)).isEqualTo(10);
        assertThat(db.connections.getFirst().statements).hasSize(2);
        for (BatchState statement : db.connections.getFirst().statements) {
            assertThat(statement.batchSizes).containsExactly(3, 2);
            assertThat(statement.closed).isTrue();
        }
    }

    @Test void invalidConfigurationAndActiveTransactionReconfigurationAreRejected() throws Exception {
        assertThat(new JdbcSessionProperties().getExecuteBatchSize()).isEqualTo(256);
        assertThatThrownBy(() -> new JdbcSessionProperties().setExecuteBatchSize(0)).isInstanceOf(IllegalArgumentException.class);
        Database db = new Database(); RecordingSession session = session(db, 0);
        assertThatThrownBy(() -> session.setExecuteBatchSize(-1)).isInstanceOf(IllegalArgumentException.class);
        JdbcSessionProperties properties = new JdbcSessionProperties(); properties.setExecuteBatchSize(64); properties.setParallelConnections(2);
        session.setProperties(properties);
        assertThat(session.getExecuteBatchSize()).isEqualTo(64);
        assertThat(session.getParallelConnections()).isEqualTo(2);
        session.startTransaction();
        try {
            assertThatThrownBy(() -> session.setExecuteBatchSize(128)).isInstanceOf(IllegalStateException.class);
        } finally { session.rollbackTransaction(); }
    }

    private RecordingSession session(Database db, int size) {
        RecordingSession session = new RecordingSession(db.source);
        session.rows = Collections.nCopies(size, new ParseResult(null, parameterNode("insert into t values (?)"), null));
        sessions.add(session);
        return session;
    }

    private static SqlNode parameterNode(String sql) {
        return new SqlNode(sql) {
            @Override public List<Object> getList() { return List.of(7); }
        };
    }

    private static final class RecordingSession extends JdbcSession {
        List<ParseResult> rows;
        int groupingCalls;
        final List<Integer> groupSizes = new CopyOnWriteArrayList<>();
        final List<String> workerThreads = new CopyOnWriteArrayList<>();
        RecordingSession(DataSource source) {
            super(source);
            setThreadPoolExecutor((ThreadPoolExecutor) Executors.newFixedThreadPool(4));
        }
        int run(boolean parallel) {
            ExecuteRequest<Object> request = ExecuteRequest.ofInsert(Object.class);
            request.setBatch(parallel); request.setBatchSize(1);
            return insert(request);
        }
        @Override protected <T extends com.yulinlin.data.core.cache.CacheKey> List<ParseResult> parseNodes(RequestType type, Object root, List<INode> nodes, Class clazz) { return rows; }
        @Override protected List<List<ParseResult>> parseNodesAndGroup(RequestType type, Object root, List<INode> nodes, Class clazz) {
            groupingCalls++;
            return super.parseNodesAndGroup(type, root, nodes, clazz);
        }
        @Override protected Integer executeUpdateNode(Connection connection, List<ParseResult> results) {
            groupSizes.add(results.size()); workerThreads.add(Thread.currentThread().getName());
            return super.executeUpdateNode(connection, results);
        }
    }

    private static final class Database {
        final DataSource source = mock(DataSource.class);
        final List<ConnectionState> connections = new CopyOnWriteArrayList<>();
        CountDownLatch firstBatches;
        int failOnBatch;
        int updateCount = 1;
        Database() throws Exception {
            when(source.getConnection()).thenAnswer(invocation -> {
                ConnectionState state = new ConnectionState(this);
                connections.add(state);
                return state.connection;
            });
        }
    }

    private static final class ConnectionState {
        final Connection connection;
        final List<BatchState> statements = new CopyOnWriteArrayList<>();
        boolean autoCommit = true;
        boolean closed;
        int commits;
        int rollbacks;
        ConnectionState(Database db) {
            connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> { autoCommit = (Boolean) args[0]; yield null; }
                        case "prepareStatement" -> {
                            BatchState state = new BatchState(db); statements.add(state); yield state.statement;
                        }
                        case "commit" -> { commits++; yield null; }
                        case "rollback" -> { rollbacks++; yield null; }
                        case "close" -> { closed = true; yield null; }
                        case "isClosed" -> closed;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "RecordingConnection";
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class BatchState {
        final PreparedStatement statement;
        final List<Integer> batchSizes = new ArrayList<>();
        final Thread owner = Thread.currentThread();
        int pending;
        int boundParameters;
        int clearBatchCalls;
        boolean closed;
        BatchState(Database db) {
            statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        if (Thread.currentThread() != owner) throw new AssertionError("statement used by a different thread");
                        return switch (method.getName()) {
                            case "setObject" -> { boundParameters++; yield null; }
                            case "clearParameters" -> null;
                            case "addBatch" -> { pending++; yield null; }
                            case "executeBatch" -> {
                                assertThat(pending).isPositive(); batchSizes.add(pending);
                                if (batchSizes.size() == 1 && db.firstBatches != null) {
                                    db.firstBatches.countDown();
                                    if (!db.firstBatches.await(10, TimeUnit.SECONDS)) throw new AssertionError("workers did not start");
                                }
                                if (db.failOnBatch == batchSizes.size()) throw new BatchUpdateException("injected batch failure", new int[0]);
                                int[] counts = new int[pending]; Arrays.fill(counts, db.updateCount); yield counts;
                            }
                            // Deliberately retain pending rows until clearBatch(), unlike some drivers.
                            case "clearBatch" -> { pending = 0; clearBatchCalls++; yield null; }
                            case "close" -> { closed = true; yield null; }
                            case "toString" -> "RecordingStatement";
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}

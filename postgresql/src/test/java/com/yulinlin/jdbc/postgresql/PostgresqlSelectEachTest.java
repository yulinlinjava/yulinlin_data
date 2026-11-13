package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresqlSelectEachTest {
    @Test void cursorTransactionEndsAndRestoresConnectionOnSuccessOrConsumerFailure() {
        for (boolean fail : List.of(false, true)) {
            var ds = new RecordingJdbc(); var session = session(ds);
            try {
                var error = new IllegalArgumentException("consumer");
                if (fail) assertSame(error, assertThrows(IllegalArgumentException.class,
                        () -> session.selectEach(request(), row -> { throw error; })));
                else assertEquals(2, session.selectEach(request(), row -> { assertEquals(Boolean.TRUE, row.flag); }));
                assertEquals(1000, ds.fetch);
                assertEquals(List.of("auto=false", "prepare", "rows-close", "statement-close", "rollback", "auto=true", "close"), ds.lifecycle);
                assertEquals(0, ds.commits);
            } finally { session.getThreadPoolExecutor().shutdownNow(); }
        }
    }

    @Test void enclosingTransactionIsNeitherCompletedNorReconfiguredByIteration() {
        var ds = new RecordingJdbc(); var session = session(ds);
        try {
            session.startTransaction();
            assertEquals(2, session.selectEach(request(), row -> { }));
            assertEquals(List.of("auto=false", "prepare", "rows-close", "statement-close"), ds.lifecycle);
            assertTrue(session.isOpenTransaction());
            session.commitTransaction();
            assertEquals(1, ds.commits);
            assertEquals(List.of("auto=false", "prepare", "rows-close", "statement-close", "commit", "auto=true", "close"), ds.lifecycle);
        } finally { if (session.isOpenTransaction()) session.rollbackTransaction(); session.getThreadPoolExecutor().shutdownNow(); }
    }

    @Test void failedRollbackIsSuppressedAndNeverFollowedByImplicitCommit() {
        var ds = new RecordingJdbc(); ds.failRollback = true; var session = session(ds);
        try {
            var original = new IllegalArgumentException("consumer");
            assertSame(original, assertThrows(IllegalArgumentException.class,
                    () -> session.selectEach(request(), row -> { throw original; })));
            assertEquals(1, original.getSuppressed().length);
            assertEquals("rollback failed", original.getSuppressed()[0].getMessage());
            assertFalse(ds.lifecycle.contains("auto=true"));
            assertEquals("close", ds.lifecycle.getLast());
            assertEquals(0, ds.commits);
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    @Test void optionalRealPostgresFetchesMultipleBatchesWithoutChangingSchema() {
        String url = System.getProperty("pg.test.url");
        assumeTrue(url != null && !url.isBlank(), "Set pg.test.url for real PostgreSQL verification");
        var ds = new DriverManagerDataSource(url, System.getProperty("pg.test.user", "postgres"),
                System.getenv().getOrDefault("PG_TEST_PASSWORD", ""));
        var session = new PostgresqlSession(ds); session.setCoderManager(new PostgresqlCoderManager());
        try {
            var request = QueryRequest.newInstance("select id, true as flag from generate_series(1, 2501) as id order by id", Map.of(), Row.class);
            int[] seen = {0};
            assertEquals(2501, session.selectEach(request, row -> { assertEquals(++seen[0], row.id); assertTrue(row.flag); }));
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    private static PostgresqlSession session(RecordingJdbc ds) {
        var session = new PostgresqlSession(ds) {
            @Override protected void printExecution(com.yulinlin.data.core.session.SessionExecution event) { }
        };
        session.setCoderManager(new PostgresqlCoderManager()); return session;
    }
    private static QueryRequest<Row> request() { return QueryRequest.newInstance("select #{id} as id, true as flag", Map.of("id", 7), Row.class); }
    public static class Row { public Integer id; public Boolean flag; }
    static class RecordingJdbc extends AbstractDataSource {
        List<String> lifecycle = new ArrayList<>(); boolean auto = true, failRollback; int fetch, commits;
        @Override public Connection getConnection() {
            return proxy(Connection.class, (o, m, a) -> switch (m.getName()) {
                case "getAutoCommit" -> auto;
                case "setAutoCommit" -> { auto = (boolean) a[0]; lifecycle.add("auto=" + auto); yield null; }
                case "isClosed" -> false;
                case "prepareStatement" -> {
                    assertFalse(auto); assertEquals(3, a.length);
                    assertEquals(ResultSet.TYPE_FORWARD_ONLY, a[1]); assertEquals(ResultSet.CONCUR_READ_ONLY, a[2]);
                    lifecycle.add("prepare"); yield statement();
                }
                case "rollback" -> { lifecycle.add("rollback"); if (failRollback) throw new SQLException("rollback failed"); yield null; }
                case "commit" -> { lifecycle.add("commit"); commits++; yield null; }
                case "close" -> { lifecycle.add("close"); yield null; }
                default -> throw new AssertionError(m.getName());
            });
        }
        @Override public Connection getConnection(String u, String p) { return getConnection(); }
        PreparedStatement statement() {
            return proxy(PreparedStatement.class, (o, m, a) -> switch (m.getName()) {
                case "setFetchSize" -> { fetch = (int) a[0]; yield null; }
                case "setObject" -> { assertEquals(7, a[1]); yield null; }
                case "executeQuery" -> rows();
                case "close" -> { lifecycle.add("statement-close"); yield null; }
                default -> throw new AssertionError(m.getName());
            });
        }
        ResultSet rows() {
            var metadata = proxy(ResultSetMetaData.class, (o, m, a) -> switch (m.getName()) {
                case "getColumnCount" -> 2;
                case "getColumnLabel" -> (int) a[0] == 1 ? "id" : "flag";
                case "getColumnType" -> (int) a[0] == 1 ? Types.INTEGER : Types.BOOLEAN;
                default -> throw new AssertionError(m.getName());
            });
            int[] at = {0};
            return proxy(ResultSet.class, (o, m, a) -> switch (m.getName()) {
                case "getMetaData" -> metadata;
                case "next" -> ++at[0] <= 2;
                case "getString" -> "7";
                case "getBoolean" -> true;
                case "wasNull" -> false;
                case "close" -> { lifecycle.add("rows-close"); yield null; }
                default -> throw new AssertionError(m.getName());
            });
        }
    }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}

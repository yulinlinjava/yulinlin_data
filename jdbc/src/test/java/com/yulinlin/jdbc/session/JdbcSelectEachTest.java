package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSelectEachTest {
    @Test void consumesBeforeNextReadAndDoesNotMaterializeOrReuseRows() {
        var jdbc = new RecordingJdbc();
        jdbc.rowCount = 20_000;
        try (var session = new TestSession(jdbc)) {
            var consumed = new AtomicInteger();
            var request = request();
            assertEquals(20_000, session.selectEach(request, row -> {
                int at = consumed.incrementAndGet();
                assertEquals(at, jdbc.nextCalls, "consumer must run before fetching the next row");
                assertEquals(at, row.id);
                assertNull(row.optional);
                row.id = -1; // No mutable row instance may be reused by the next callback.
            }));
            assertEquals(1, jdbc.executions);
            assertEquals(List.of("O'Reilly"), jdbc.bound);
            assertFalse(jdbc.sql.contains("O'Reilly"));
            assertEquals(1000, jdbc.fetchSize);
            assertEquals(List.of(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY), jdbc.resultOptions);
            assertEquals(1, jdbc.metadataReads);
            assertTrue(jdbc.rowsClosed && jdbc.statementClosed && jdbc.connectionClosed);
            assertEquals(1, session.events.size());
            assertNull(session.events.getFirst().error());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"prepare", "bind", "execute", "read", "decode", "consumer"})
    void everyFailureClosesOwnedResourcesAndNeverRetries(String phase) {
        var jdbc = new RecordingJdbc(); jdbc.failurePhase = phase;
        try (var session = new TestSession(jdbc)) {
            var original = new IllegalArgumentException("business failure");
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> session.selectEach(request(), row -> { throw original; }));
            if (phase.equals("consumer")) assertSame(original, thrown);
            else if (!phase.equals("decode")) assertSame(jdbc.failure, assertInstanceOf(OrmException.class, thrown).getCause());
            assertTrue(jdbc.connectionClosed);
            assertEquals(!phase.equals("prepare"), jdbc.statementClosed);
            assertEquals(Set.of("read", "decode", "consumer").contains(phase), jdbc.rowsClosed);
            assertTrue(jdbc.executions <= 1);
            assertSame(thrown, session.events.getFirst().error());
        }
    }

    @Test void listStillUsesOrdinaryStatementAndTheSameDecoder() {
        var jdbc = new RecordingJdbc();
        try (var session = new TestSession(jdbc)) {
            session.setProxyService(com.yulinlin.data.core.proxy.EntityProxyService.newInstance());
            var list = session.select(request());
            assertEquals(List.of(1, 2, 3), list.stream().map(r -> r.id).toList());
            assertNull(jdbc.fetchSize);
            assertTrue(jdbc.resultOptions.isEmpty());
            assertTrue(jdbc.rowsClosed && jdbc.statementClosed && jdbc.connectionClosed);
        }
    }

    @Test void callbackCannotReenterSessionAndGuardIsReleasedAfterFailure() {
        var jdbc = new RecordingJdbc();
        try (var session = new TestSession(jdbc)) {
            assertThrows(IllegalStateException.class,
                    () -> session.selectEach(request(), row -> session.selectEach(request(), ignored -> { })));
            assertEquals(1, jdbc.acquisitions);
            assertEquals(3, session.selectEach(request(), row -> { }));
            assertEquals(2, jdbc.acquisitions);
        }
    }

    @Test void unsupportedBackendAndInvalidOptionsFailBeforeOpeningConnection() {
        var jdbc = new RecordingJdbc(); var session = new JdbcSession(jdbc);
        try {
            assertThrows(UnsupportedOperationException.class, () -> session.selectEach(request(), row -> { }));
            assertThrows(IllegalArgumentException.class, () -> session.selectEach(request().cache(), row -> { }));
            assertThrows(IllegalArgumentException.class, () -> session.selectEach(request().autoUpdate(), row -> { }));
            assertThrows(NullPointerException.class, () -> session.selectEach(request(), null));
            assertEquals(0, jdbc.acquisitions);
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    static QueryRequest<Row> request() {
        var select = new SelectWrapper<>().table("samples"); select.where().eq("name", "O'Reilly");
        return QueryRequest.newInstance(Row.class, select);
    }
    public static class Row { public Integer id; public String optional; }
    static class TestSession extends JdbcSession implements AutoCloseable {
        final List<SessionExecution> events = new ArrayList<>();
        TestSession(RecordingJdbc jdbc) { super(jdbc); setCoderManager(new JdbcCoderManager()); setExecutionListeners(List.of(events::add)); }
        @Override protected int selectEachFetchSize() { return 1000; }
        @Override protected void printExecution(SessionExecution event) { }
        @Override public void close() { getThreadPoolExecutor().shutdownNow(); }
    }
    static class RecordingJdbc extends AbstractDataSource {
        int acquisitions, executions, nextCalls, metadataReads, rowCount = 3, cursor;
        Integer fetchSize;
        boolean connectionClosed, statementClosed, rowsClosed;
        String sql, failurePhase;
        SQLException failure = new SQLException("test SQL failure", "42000");
        List<Object> bound = new ArrayList<>();
        List<Object> resultOptions = List.of();
        @Override public Connection getConnection() {
            acquisitions++; cursor = 0; bound.clear();
            return proxy(Connection.class, (o, m, a) -> switch (m.getName()) {
                case "prepareStatement" -> {
                    if ("prepare".equals(failurePhase)) throw failure;
                    sql = (String) a[0]; resultOptions = Arrays.asList(a).subList(1, a.length); yield statement();
                }
                case "close" -> { connectionClosed = true; yield null; }
                default -> throw new AssertionError(m.getName());
            });
        }
        @Override public Connection getConnection(String u, String p) { return getConnection(); }
        PreparedStatement statement() {
            return proxy(PreparedStatement.class, (o, m, a) -> switch (m.getName()) {
                case "setFetchSize" -> { fetchSize = (int) a[0]; yield null; }
                case "setObject" -> { if ("bind".equals(failurePhase)) throw failure; bound.add(a[1]); yield null; }
                case "executeQuery" -> { executions++; if ("execute".equals(failurePhase)) throw failure; yield rows(); }
                case "close" -> { statementClosed = true; yield null; }
                default -> throw new AssertionError(m.getName());
            });
        }
        ResultSet rows() {
            var metadata = proxy(ResultSetMetaData.class, (o, m, a) -> switch (m.getName()) {
                case "getColumnCount" -> 2;
                case "getColumnLabel" -> (int) a[0] == 1 ? "id" : "optional";
                case "getColumnType" -> (int) a[0] == 1 ? Types.INTEGER : Types.VARCHAR;
                default -> throw new AssertionError(m.getName());
            });
            return proxy(ResultSet.class, (o, m, a) -> switch (m.getName()) {
                case "getMetaData" -> { metadataReads++; yield metadata; }
                case "next" -> { nextCalls++; if ("read".equals(failurePhase)) throw failure; yield ++cursor <= rowCount; }
                case "getString" -> a[0].equals("optional") ? null : "decode".equals(failurePhase) ? "invalid-integer" : String.valueOf(cursor);
                case "close" -> { rowsClosed = true; yield null; }
                default -> throw new AssertionError(m.getName());
            });
        }
    }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}

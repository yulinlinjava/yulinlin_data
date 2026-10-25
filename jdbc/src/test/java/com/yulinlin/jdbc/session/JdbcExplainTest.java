package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JdbcExplainTest {
    @Test
    void nativeLabelsNullsTypesAndRowOrderArePreservedAndResourcesClose() {
        var jdbc = new RecordingJdbc();
        try (var session = new TestSession(jdbc)) {
            var select = new SelectWrapper<>().table("sample");
            String value = "${column}#{id}'";
            select.where().eq("name", value);
            var rows = session.explain(QueryRequest.newInstance(Map.class, select));
            assertEquals(2, rows.size());
            assertEquals(List.of("id", "possible_keys", "Extra"), new ArrayList<>(rows.getFirst().keySet()));
            assertInstanceOf(Integer.class, rows.getFirst().get("id"));
            assertEquals(12, rows.getFirst().get("id"));
            assertTrue(rows.getFirst().containsKey("possible_keys"));
            assertNull(rows.getFirst().get("possible_keys"));
            assertEquals("native plan", rows.getFirst().get("Extra"));
            assertEquals(13, rows.getLast().get("id"));
            assertEquals(List.of(value), jdbc.bound);
            assertTrue(jdbc.sql.startsWith("EXPLAIN select "));
            assertFalse(jdbc.sql.contains(value));
            assertFalse(jdbc.sql.contains("ANALYZE"));
            assertEquals(1, jdbc.executions);
            assertTrue(jdbc.resultClosed && jdbc.statementClosed && jdbc.connectionClosed);
            assertEquals(ParseType.explain, session.events.getFirst().requests().getFirst().getType());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"execute", "read"})
    void failuresPropagateWithoutRunningTheOriginalQueryAndReleaseResources(String phase) {
        var jdbc = new RecordingJdbc();
        jdbc.failurePhase = phase;
        try (var session = new TestSession(jdbc)) {
            var request = QueryRequest.newInstance(Map.class, new SelectWrapper<>().table("sample"));
            assertSame(jdbc.failure, assertThrows(SQLException.class, () -> session.explainCount(request)));
            assertEquals(1, jdbc.executions);
            assertTrue(jdbc.statementClosed && jdbc.connectionClosed);
            assertEquals(phase.equals("read"), jdbc.resultClosed);
            assertSame(jdbc.failure, session.events.getFirst().error());
            assertTrue(jdbc.sql.startsWith("EXPLAIN select count(1)"));
        }
    }

    @Test
    void unadaptedJdbcSessionRejectsDiagnosticsBeforeGettingAConnection() {
        var jdbc = new RecordingJdbc();
        var session = new JdbcSession(jdbc);
        try {
            var request = QueryRequest.newInstance(Map.class, new SelectWrapper<>().table("sample"));
            assertThrows(UnsupportedOperationException.class, () -> session.explain(request));
            assertThrows(UnsupportedOperationException.class, () -> session.explainCount(request));
            assertEquals(0, jdbc.connections);
            assertEquals(0, jdbc.executions);
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    private static final class TestSession extends JdbcSession implements AutoCloseable {
        final List<SessionExecution> events = new ArrayList<>();
        TestSession(RecordingJdbc jdbc) {
            super(jdbc);
            setCoderManager(new JdbcCoderManager());
            setExecutionListeners(List.of(events::add));
        }
        @Override protected String explainPrefix() { return "EXPLAIN "; }
        @Override protected void printExecution(SessionExecution execution) { }
        @Override public void close() { getThreadPoolExecutor().shutdownNow(); }
    }

    private static final class RecordingJdbc extends AbstractDataSource {
        String sql;
        int connections, executions, cursor;
        String failurePhase;
        final SQLException failure = new SQLException("diagnostic failed");
        final List<Object> bound = new ArrayList<>();
        boolean connectionClosed, statementClosed, resultClosed;
        final String[] labels = {"id", "possible_keys", "Extra"};
        final Object[][] values = {{12, null, "native plan"}, {13, "idx", "second row"}};

        @Override public Connection getConnection() {
            connections++;
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> { sql = (String) args[0]; yield statement(); }
                case "close" -> { connectionClosed = true; yield null; }
                default -> throw new AssertionError(method.getName());
            });
        }
        @Override public Connection getConnection(String username, String password) { return getConnection(); }

        PreparedStatement statement() {
            return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
                case "setObject" -> { assertEquals(bound.size() + 1, args[0]); bound.add(args[1]); yield null; }
                case "executeQuery" -> {
                    executions++;
                    if ("execute".equals(failurePhase)) throw failure;
                    yield rows();
                }
                case "close" -> { statementClosed = true; yield null; }
                default -> throw new AssertionError(method.getName());
            });
        }

        ResultSet rows() {
            ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (object, method, args) -> switch (method.getName()) {
                case "getColumnCount" -> labels.length;
                case "getColumnLabel" -> labels[(int) args[0] - 1];
                default -> throw new AssertionError(method.getName());
            });
            return proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
                case "getMetaData" -> metadata;
                case "next" -> {
                    if ("read".equals(failurePhase)) throw failure;
                    yield cursor++ < values.length;
                }
                case "getObject" -> values[cursor - 1][(int) args[0] - 1];
                case "close" -> { resultClosed = true; yield null; }
                default -> throw new AssertionError(method.getName());
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}

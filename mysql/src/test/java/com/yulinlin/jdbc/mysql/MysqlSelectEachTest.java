package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.jdbc.mysql.coder.MysqlCoderManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MysqlSelectEachTest {
    @Test void mysqlRequestsRowStreamingWhileOrdinaryListKeepsDefaultStatement() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:mysql_each;MODE=MYSQL;DATABASE_TO_LOWER=TRUE");
        var calls = new ArrayList<List<Object>>();
        var ds = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = source.getConnection();
                return wrap(Connection.class, connection, (method, args) -> {
                    if (!method.getName().equals("prepareStatement")) return invoke(method, connection, args);
                    calls.add(Arrays.asList(args));
                    var statement = (PreparedStatement) invoke(method, connection, args);
                    return wrap(PreparedStatement.class, statement, (operation, values) -> {
                        if (operation.getName().equals("setFetchSize")) {
                            assertEquals(Integer.MIN_VALUE, values[0]);
                            calls.add(List.of("fetch", values[0]));
                            return null; // H2 cannot implement Connector/J's negative streaming sentinel.
                        }
                        return invoke(operation, statement, values);
                    });
                });
            }
            @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
        };
        var session = new MysqlSession(ds); session.setCoderManager(new MysqlCoderManager());
        session.setProxyService(com.yulinlin.data.core.proxy.EntityProxyService.newInstance());
        try {
            var request = QueryRequest.newInstance("select #{value} as `id`", Map.of("value", 42), Row.class);
            var rows = new ArrayList<Row>();
            assertEquals(1, session.selectEach(request, rows::add));
            assertEquals(42, rows.getFirst().id);
            assertEquals(List.of(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY), calls.getFirst().subList(1, 3));
            assertEquals(List.of("fetch", Integer.MIN_VALUE), calls.get(1));
            assertEquals(42, session.select(request).getFirst().id);
            assertEquals(1, calls.getLast().size(), "selectList uses an ordinary prepared statement");
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    @Test void optionalRealConnectorJStreamsAndCanQueryAgainAfterCallbackFailure() {
        String url = System.getProperty("mysql.test.url");
        assumeTrue(url != null && !url.isBlank(), "Set mysql.test.url for real MySQL verification");
        var ds = new DriverManagerDataSource(url, System.getProperty("mysql.test.user", "root"),
                System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
        var session = new MysqlSession(ds); session.setCoderManager(new MysqlCoderManager());
        try {
            var request = QueryRequest.newInstance("select #{value} as `id` union all select 43", Map.of("value", 42), Row.class);
            var failure = new IllegalArgumentException("stop");
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> session.selectEach(request, row -> { throw failure; })));
            var rows = new ArrayList<Row>();
            assertEquals(2, session.selectEach(request, rows::add));
            assertEquals(List.of(42, 43), rows.stream().map(row -> row.id).toList());
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    public static class Row { public Integer id; }
    interface Call { Object run(Method method, Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked") static <T> T wrap(Class<T> type, Object target, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> call.run(method, args));
    }
    static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
}

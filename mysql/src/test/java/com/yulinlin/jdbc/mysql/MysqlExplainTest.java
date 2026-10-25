package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.mysql.coder.MysqlCoderManager;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real dialect and session parsing, capturing execution without an external database. */
class MysqlExplainTest {
    @Test
    void listGroupAndDerivedCountPlansRetainBindingsAndIgnoreIndexHints() {
        var dataSource = new ConnectionStub();
        var session = new CapturingSession(dataSource);
        try {
            var select = new SelectWrapper<>().table("samples", "s").page(2, 5).orderByAsc("id");
            select.fields().field("id");
            select.where().eq("name", "O'Reilly");
            var request = QueryRequest.newInstance(Map.class, select).useIndex("missing_index");
            assertSame(session.nativeRows, session.explain(request));
            String listSql = session.sql.getSql();
            assertTrue(listSql.startsWith("EXPLAIN select "));
            assertTrue(listSql.toLowerCase().contains("limit"));
            assertTrue(listSql.contains("order by"));
            assertFalse(listSql.contains("USE INDEX"));
            assertFalse(listSql.contains("missing_index"));
            assertFalse(listSql.contains("ANALYZE"));
            assertFalse(listSql.contains("O'Reilly"));
            assertEquals(List.of("O'Reilly"), session.sql.getList());
            assertSame(session.nativeRows, session.explainCount(request));
            String countSql = session.sql.getSql();
            assertTrue(countSql.startsWith("EXPLAIN select count(1) as total from `samples` `s` where "));
            assertFalse(countSql.toLowerCase().contains("limit"));
            assertFalse(countSql.contains("order by"));
            assertFalse(countSql.contains("USE INDEX"));
            assertEquals(List.of("O'Reilly"), session.sql.getList());
            session.explain(request);
            assertEquals(listSql, session.sql.getSql(), "Diagnostics must not mutate the original query");

            var group = new GroupWrapper<>().table("samples").page(2, 5);
            group.aggregations().field("name", "name");
            group.metrics().sum("amount", "amount");
            group.where().eq("name", "O'Reilly");
            group.having().gt("amount", 2);
            var grouped = QueryRequest.newInstance(Map.class, group).useIndex("missing_index");
            session.explain(grouped);
            assertTrue(session.sql.getSql().toLowerCase().contains("limit"));
            session.explainCount(grouped);
            assertTrue(session.sql.getSql().startsWith("EXPLAIN select count(1) as total from ( select "));
            assertTrue(session.sql.getSql().contains("group by"));
            assertTrue(session.sql.getSql().contains("having"));
            assertFalse(session.sql.getSql().toLowerCase().contains("limit"));
            assertEquals(List.of("O'Reilly", 2), session.sql.getList());
            String groupedCount = session.sql.getSql();
            session.explain(grouped.copy(new CountWrapper(group)));
            assertEquals(groupedCount, session.sql.getSql());
            assertEquals(6, dataSource.opened);
            assertEquals(dataSource.opened, dataSource.closed);
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    private static class CapturingSession extends MysqlSession {
        SqlNode sql;
        final List<Map<String, Object>> nativeRows = List.of(Map.of("native_label", "native plan"));
        CapturingSession(ConnectionStub dataSource) {
            super(dataSource);
            setCoderManager(new MysqlCoderManager());
        }
        @Override protected List<Map<String, Object>> executeExplainNode(Connection connection, SqlNode node) {
            sql = node;
            return nativeRows;
        }
        @Override protected void printExecution(SessionExecution execution) { }
    }

    private static class ConnectionStub extends AbstractDataSource {
        int opened, closed;
        @Override public Connection getConnection() {
            opened++;
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (object, method, args) -> {
                        if (method.getName().equals("close")) { closed++; return null; }
                        throw new AssertionError(method.getName());
                    });
        }
        @Override public Connection getConnection(String username, String password) { return getConnection(); }
    }
}

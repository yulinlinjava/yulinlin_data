package com.yulinlin.mcp;

import com.yulinlin.jdbc.h2.H2Session;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import com.yulinlin.mcp.data.McpDataService;
import com.yulinlin.mcp.data.McpDataService.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.sql.Connection;
import java.util.*;

import static com.yulinlin.mcp.McpTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class McpDataServiceTest extends OrmTestSupport {
    private Connection anchor;
    private McpProperties properties;
    private MemoryStore store;
    private McpDataService data;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:mcp_data_" + UUID.randomUUID());
        anchor = ds.getConnection();
        try (var s = anchor.createStatement()) {
            s.execute("create table orders (id bigint primary key, status varchar(20), amount decimal(12,2), note varchar(50), created_at timestamp)");
            s.execute("comment on table orders is '订单'");
            for (int i = 1; i <= 10; i++)
                s.execute("insert into orders values (" + i + ", '" + (i % 2 == 0 ? "paid" : "new") + "', " + i + ".50, "
                        + (i == 3 ? "null" : "'n" + i + "'") + ", timestamp '2026-11-0" + Math.min(i, 9) + " 10:00:00')");
        }
        var session = new H2Session(ds);
        session.setCoderManager(new H2CoderManager());
        install(session);
        properties = properties();
        properties.getData().setRead(List.of("*"));
        properties.getData().setWrite(List.of("mcp-test"));
        properties.getData().setMaxRows(5);
        store = new MemoryStore();
        data = new McpDataService(properties, store, JSON, null);
        data.initialize();
    }

    private long count(String where) throws Exception {
        try (var s = anchor.createStatement(); var rs = s.executeQuery("select count(*) from orders where " + where)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void sourcesAndSchemaDescribeTheProjectDataSources() {
        var sources = (List<Map<String, Object>>) data.describeSources().get("sources");
        assertEquals(1, sources.size());
        assertEquals("mcp-test", sources.get(0).get("group"));
        assertEquals("jdbc", sources.get(0).get("type"));
        assertEquals(true, sources.get(0).get("writable"));
        assertTrue(String.valueOf(sources.get(0).get("database")).startsWith("H2"));

        var tables = (List<Map<String, Object>>) data.schema("mcp-test", null).get("tables");
        assertTrue(tables.stream().anyMatch(t -> "ORDERS".equals(t.get("name")) && "订单".equals(t.get("comment"))));
        var detail = (List<Map<String, Object>>) data.schema("mcp-test", "order").get("tables");
        assertEquals(List.of("ID"), detail.get(0).get("primaryKey"));
        assertThrows(IllegalArgumentException.class, () -> data.schema("missing", null));
    }

    @Test
    void queryReturnsTypedValuesNullColumnsAndTruncates() {
        var params = new HashMap<String, Object>();
        params.put("ids", List.of(3L, 4L));
        var result = data.query("mcp-test", "select id, amount, note, created_at from orders where id in (#{ids}) order by id", params, 100);
        assertEquals(List.of("ID", "AMOUNT", "NOTE", "CREATED_AT"), result.get("columns"));
        var rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(3L, rows.get(0).get("ID"));
        assertEquals(3.5, rows.get(0).get("AMOUNT"));
        assertTrue(rows.get(0).containsKey("NOTE"));
        assertNull(rows.get(0).get("NOTE"));
        assertEquals("2026-11-03 10:00:00", rows.get(0).get("CREATED_AT"));
        assertEquals(false, result.get("truncated"));

        var all = data.query("mcp-test", "select id from orders order by id", Map.of(), 100);
        assertEquals(5, all.get("rowCount"));
        assertEquals(true, all.get("truncated"));
        assertEquals(2, data.query("mcp-test", "select id from orders", Map.of(), 2).get("rowCount"));
    }

    @Test
    void queryRejectsWritesAndTextSubstitution() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> data.query("mcp-test", "delete from orders where id = 1", Map.of(), 5));
        assertThrows(IllegalArgumentException.class, () -> data.query("mcp-test", "select * from ${t}", Map.of(), 5));
        assertThrows(IllegalArgumentException.class, () -> data.query("mcp-test", "select * from orders where status = '#{s}'", Map.of("s", "x"), 5));
        var error = assertThrows(IllegalArgumentException.class, () -> data.query("mcp-test", "select * from no_such_table", Map.of(), 5));
        assertTrue(error.getMessage().startsWith("执行失败"));
        assertEquals(10, count("1=1"));
    }

    @Test
    void executeDryRunCommitMismatchAndReplay() throws Exception {
        var ctx = ctx("cred");
        var update = List.of(new Statement("update orders set status = #{to} where status = #{from}", Map.of("to", "done", "from", "paid")),
                new Statement("select count(*) as c from orders where status = 'done'", Map.of()));

        var dry = data.execute(ctx, "mcp-test", update, true, null, null);
        assertEquals(5L, ((Number) dry.get("totalAffectedRows")).longValue());
        assertEquals(false, dry.get("committed"));
        var results = (List<Map<String, Object>>) dry.get("results");
        assertEquals(5L, ((List<Map<String, Object>>) results.get(1).get("rows")).get(0).get("C"));
        assertEquals(0, count("status = 'done'"));

        assertThrows(IllegalArgumentException.class, () -> data.execute(ctx, "mcp-test", update, false, null, null));
        var mismatch = assertThrows(IllegalArgumentException.class, () -> data.execute(ctx, "mcp-test", update, false, 4L, null));
        assertTrue(mismatch.getMessage().contains("已全部回滚"));
        assertEquals(0, count("status = 'done'"));

        var done = data.execute(ctx, "mcp-test", update, false, 5L, "req-1");
        assertEquals(true, done.get("committed"));
        assertEquals(5, count("status = 'done'"));
        var replay = data.execute(ctx, "mcp-test", update, false, 5L, "req-1");
        assertEquals(true, replay.get("replayed"));
        assertThrows(McpException.class, () -> data.execute(ctx, "mcp-test",
                List.of(new Statement("delete from orders where id = 1", Map.of())), false, 1L, "req-1"));
        assertEquals(10, count("1=1"));

        var unknown = data.execute(ctx, "mcp-test", List.of(new Statement("update orders set note = #{n} where id = 1", mapOf("n", null))), false, -1L, null);
        assertTrue(String.valueOf(unknown.get("warning")).contains("expectAffectedRows=-1"));
        assertEquals(1, count("id = 1 and note is null"));
    }

    @Test
    void failingStatementRollsBackWholeTransaction() throws Exception {
        var statements = List.of(new Statement("delete from orders where id = 1", Map.of()),
                new Statement("insert into orders(id, status) values (2, 'dup')", Map.of()));
        var error = assertThrows(IllegalArgumentException.class, () -> data.execute(ctx("c"), "mcp-test", statements, false, 2L, null));
        assertTrue(error.getMessage().startsWith("第 1 条语句失败"));
        assertEquals(10, count("1=1"));
    }

    @Test
    void readOnlyGroupsCannotWrite() {
        properties.getData().setWrite(List.of());
        var readOnly = new McpDataService(properties, store, JSON, null);
        assertFalse(readOnly.writeEnabled());
        assertThrows(IllegalArgumentException.class, () -> readOnly.execute(ctx("c"), "mcp-test",
                List.of(new Statement("delete from orders where id = 1", Map.of())), true, null, null));
    }

    @Test
    void unknownConfiguredGroupFailsStartup() {
        properties.getData().setRead(List.of("nope"));
        var broken = new McpDataService(properties, store, JSON, null);
        assertThrows(IllegalStateException.class, broken::initialize);
    }

    @Test
    void scriptScopeReadsAndWritesInOneTransaction() throws Exception {
        var analysis = data.script(ctx("c"), null, false, null, null, "s", scope -> {
            var rows = scope.query("mcp-test", "select id from orders", Map.of());
            assertEquals(10, rows.size(), "脚本不按 maxRows 截断");
            assertThrows(IllegalArgumentException.class, () -> scope.execute("mcp-test", "delete from orders where id = 1", Map.of()));
            return Map.of("result", rows.size());
        });
        assertEquals(10, analysis.get("result"));

        var dry = data.script(ctx("c"), "mcp-test", true, null, null, "s", scope -> {
            long n = scope.execute("mcp-test", "delete from orders where id <= #{max}", Map.of("max", 3));
            var left = scope.queryOne("mcp-test", "select count(*) as c from orders", Map.of());
            return Map.of("result", List.of(n, left.get("C")));
        });
        assertEquals(List.of(3L, 7L), dry.get("result"));
        assertEquals(3L, ((Number) dry.get("totalAffectedRows")).longValue());
        assertEquals(10, count("1=1"));
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        var map = new HashMap<String, Object>();
        map.put(key, value);
        return map;
    }
}

package com.yulinlin.mcp.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.data.McpDataScope;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptSandboxTest {
    static final ObjectMapper JSON = new ObjectMapper();
    ScriptSandbox sandbox;

    @BeforeEach void open() { sandbox = new ScriptSandbox(JSON); }
    @AfterEach void close() { sandbox.close(); }

    /** 记录调用参数、返回固定数据的假数据访问能力。 */
    static class FakeScope implements McpDataScope {
        final List<String> calls = new ArrayList<>();
        public List<Map<String, Object>> query(String group, String sql, Map<String, Object> params) {
            calls.add("query " + group + " " + sql + " " + params);
            return List.of(Map.of("id", "a1", "amount", 10.5), Map.of("id", "a2", "amount", 20L));
        }
        public Map<String, Object> queryOne(String group, String sql, Map<String, Object> params) {
            calls.add("queryOne " + sql);
            return null;
        }
        public long execute(String group, String sql, Map<String, Object> params) {
            calls.add("execute " + group + " " + params);
            return 3;
        }
    }

    @Test void runsScriptWithInjectedFunctionsAndReturnsJson() {
        var scope = new FakeScope();
        var out = sandbox.run("""
                const rows = query('report', 'select * from t where id in (#{ids})', {ids: ['a1', 'a2']});
                const total = rows.reduce((s, r) => s + r.amount, 0);
                log('rows', rows.length, {total});
                const none = queryOne('report', 'select 1');
                const n = execute('biz', 'update t set a = 1 where id = #{id}', {id: 'a1'});
                return {total, none, n, first: rows[0].id};
                """, scope, 5000);
        assertEquals(30.5, out.result().get("total").asDouble());
        assertTrue(out.result().get("none").isNull());
        assertEquals(3, out.result().get("n").asInt());
        assertEquals("a1", out.result().get("first").asText());
        assertEquals(List.of("rows 2 {\"total\":30.5}"), out.logs());
        assertEquals("query report select * from t where id in (#{ids}) {ids=[a1, a2]}", scope.calls.getFirst());
        assertTrue(scope.calls.contains("execute biz {id=a1}"));
    }

    @Test void objectCommandsArePassedAsJsonText() {
        var scope = new FakeScope();
        sandbox.run("query('es', {search: 'orders', query: {term: {status: '#{s}'}}}, {s: 'paid'}); return null;", scope, 5000);
        assertEquals("query es {\"search\":\"orders\",\"query\":{\"term\":{\"status\":\"#{s}\"}}} {s=paid}", scope.calls.getFirst());
    }

    @Test void undefinedReturnBecomesNull() {
        assertTrue(sandbox.run("const x = 1;", new FakeScope(), 5000).result().isNull());
    }

    @Test void hostJavaFileAndNetworkAccessAreUnavailable() {
        var probe = sandbox.run("return {require: typeof require, process: typeof process, fetch: typeof fetch};",
                new FakeScope(), 5000).result();
        probe.fields().forEachRemaining(e -> assertEquals("undefined", e.getValue().asText(), e.getKey()));
        // 部分全局对象（Java、load、Polyglot）可能存在，但对应能力都被沙箱拒绝
        for (var attack : List.of(
                "return Java.type('java.lang.System').getProperty('user.home');",
                "return load('/etc/passwd');",
                "return __host.query.getClass();"))
            assertThrows(IllegalArgumentException.class, () -> sandbox.run(attack, new FakeScope(), 5000), attack);
    }

    @Test void infiniteLoopIsStoppedByTimeout() {
        long start = System.currentTimeMillis();
        var e = assertThrows(IllegalArgumentException.class, () -> sandbox.run("while (true) {}", new FakeScope(), 500));
        assertTrue(e.getMessage().contains("已终止"), e.getMessage());
        assertTrue(System.currentTimeMillis() - start < 10_000);
    }

    @Test void scriptErrorsReportUserLineNumbers() {
        var e = assertThrows(IllegalArgumentException.class, () -> sandbox.run("const a = 1;\nthrow new Error('数量不对');", new FakeScope(), 5000));
        assertTrue(e.getMessage().contains("数量不对"), e.getMessage());
        assertTrue(e.getMessage().contains("第 2 行"), e.getMessage());
    }

    @Test void hostFailuresPropagateUnchanged() {
        var failing = new FakeScope() {
            @Override public List<Map<String, Object>> query(String group, String sql, Map<String, Object> params) {
                throw new IllegalArgumentException("SQL 执行失败: 表不存在");
            }
        };
        var e = assertThrows(IllegalArgumentException.class, () -> sandbox.run("return query('g', 'select * from missing');", failing, 5000));
        assertEquals("SQL 执行失败: 表不存在", e.getMessage());
    }

    @Test void oversizedScriptAndOutputAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> sandbox.run("x".repeat(ScriptSandbox.MAX_SCRIPT_CHARS + 1), new FakeScope(), 5000));
        var e = assertThrows(IllegalArgumentException.class, () -> sandbox.run("return 'x'.repeat(1100000);", new FakeScope(), 5000));
        assertTrue(e.getMessage().contains("1 MB"));
    }
}

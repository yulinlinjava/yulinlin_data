package com.yulinlin.mcp;

import com.yulinlin.mcp.store.McpAudit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.yulinlin.mcp.McpTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** 业务的 McpAuditTargetProvider 只追加字段，不会让数据工具（data.*）的审计字段消失。 */
class McpAuditTargetsTest {
    @Test
    void dataToolFieldsSurviveBusinessProvider() throws Exception {
        var audit = new McpAudit(new MemoryStore(), properties(), (tool, args) -> Map.of("tenant", "t1"));
        var args = JSON.readTree("{\"group\":\"biz\",\"command\":\"select * from orders where id = #{id}\",\"params\":{\"id\":\"secret\"}}");
        var target = audit.begin(principal("c1"), "data.query", args).target();
        assertEquals("biz", target.get("group"));
        assertEquals("select * from orders where id = #{id}", target.get("command"));
        assertEquals("t1", target.get("tenant"));
        assertFalse(target.containsValue("secret"), "参数值不进入审计");
    }

    @Test
    void businessFieldsOverrideSameNameAndOtherToolsOnlyGetBusinessFields() throws Exception {
        var audit = new McpAudit(new MemoryStore(), properties(), (tool, args) -> Map.of("group", "override"));
        assertEquals("override", audit.begin(principal("c1"), "data.schema", JSON.readTree("{\"group\":\"biz\"}")).target().get("group"));
        var other = audit.begin(principal("c1"), "order_query", JSON.readTree("{\"group\":\"x\"}")).target();
        assertEquals(Map.of("group", "override"), other);
    }

    @Test
    void defaultConstructorStillRecordsDataFields() throws Exception {
        var audit = new McpAudit(new MemoryStore(), properties());
        var target = audit.begin(principal("c1"), "data.execute",
                JSON.readTree("{\"group\":\"biz\",\"dryRun\":true,\"statements\":[{\"sql\":\"delete from t where id = 1\"}]}")).target();
        assertEquals("true", target.get("dryRun"));
        assertEquals("1", target.get("statements"));
        assertEquals("delete from t where id = 1", target.get("sql0"));
    }
}

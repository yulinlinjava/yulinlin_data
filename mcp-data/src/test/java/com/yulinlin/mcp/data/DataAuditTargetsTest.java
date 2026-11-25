package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataAuditTargetsTest {
    static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void recordsGroupCommandAndWriteControlsButNotParameterValues() throws Exception {
        var query = DataAuditTargets.of("data.query",
                JSON.readTree("{\"group\":\"biz\",\"command\":\"select * from t where id = #{id}\",\"params\":{\"id\":\"secret\"}}"));
        assertEquals("biz", query.get("group"));
        assertEquals("select * from t where id = #{id}", query.get("command"));
        assertFalse(query.containsValue("secret"));

        var execute = DataAuditTargets.of("data.execute",
                JSON.readTree("{\"group\":\"biz\",\"dryRun\":true,\"expectAffectedRows\":3,\"statements\":[{\"sql\":\"delete from t where id = 1\"}]}"));
        assertEquals("true", execute.get("dryRun"));
        assertEquals("3", execute.get("expectAffectedRows"));
        assertEquals("1", execute.get("statements"));
        assertEquals("delete from t where id = 1", execute.get("sql0"));

        assertEquals(Map.of(), DataAuditTargets.of("order_query", JSON.readTree("{}")));
    }
}

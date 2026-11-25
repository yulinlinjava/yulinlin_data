package com.yulinlin.mcp.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.McpProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpsAuditTargetsTest {
    static final ObjectMapper JSON = new ObjectMapper();

    static McpProperties properties() {
        var p = new McpProperties();
        p.setApplicationId("test");
        p.setInstanceId("server-01");
        return p;
    }

    @Test
    void commandTextCwdAndInstanceAreRecorded() throws Exception {
        var target = new OpsAuditTargets(properties()).target("command_execute",
                JSON.readTree("{\"command\":\"systemctl restart nginx\",\"cwd\":\"/opt\",\"requestId\":\"r1\"}"));
        assertEquals("server-01", target.get("instance"));
        assertEquals("systemctl restart nginx", target.get("command"));
        assertEquals("/opt", target.get("cwd"));
        assertEquals("r1", target.get("requestId"));
    }

    @Test
    void commandTextCanBeTurnedOffAndOtherToolsGetIdentifiers() throws Exception {
        var p = properties();
        p.getCommand().setAuditCommand(false);
        var targets = new OpsAuditTargets(p);
        var command = targets.target("command_execute", JSON.readTree("{\"command\":\"echo secret\"}"));
        assertFalse(command.containsKey("command"));
        assertEquals("11", command.get("commandLength"));
        assertEquals("abc", targets.target("task_output", JSON.readTree("{\"id\":\"abc\"}")).get("taskId"));
        assertEquals("42", targets.target("process_stop", JSON.readTree("{\"pid\":42,\"force\":true}")).get("pid"));
        assertEquals(Map.of(), targets.target("order_query", JSON.readTree("{}")));
    }
}

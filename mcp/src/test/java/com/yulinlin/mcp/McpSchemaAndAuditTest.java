package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.McpCredentials;
import com.yulinlin.mcp.store.McpAudit;
import com.yulinlin.mcp.tool.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpSchemaAndAuditTest extends McpTestSupport {
    @Test void referencesNestedConstraintsAndOutputSchemaAreValidated() throws Exception {
        var input=JSON.readTree("""
            {"type":"object","$defs":{"entry":{"type":"object","required":["id"],"properties":{"id":{"type":"string","pattern":"^[a-z]+$","minLength":2}},"additionalProperties":false}},
             "properties":{"items":{"type":"array","minItems":1,"maxItems":2,"items":{"$ref":"#/$defs/entry"}}},"required":["items"],"additionalProperties":false}
            """);
        var output=JSON.readTree("""
            {"type":"object","required":["data"],"properties":{"data":{"type":"integer"}},"additionalProperties":false}
            """);
        var t=new McpToolDefinition("nested","nested",input,output,true,(c,a)->1);
        var registry=new McpToolRegistry(List.of(()->List.of(t)),new Policy());
        var context=new McpCallContext("app","c",ADMIN,"request","");
        assertEquals(1,registry.call(context,"nested",JSON.readTree("{\"items\":[{\"id\":\"ab\"}]}")));
        for(String bad:List.of("{\"items\":[]}","{\"items\":[{\"id\":\"1\"}]}","{\"items\":[{\"id\":\"ab\",\"extra\":true}]}"))
            assertThrows(IllegalArgumentException.class,()->registry.call(context,"nested",JSON.readTree(bad)));
        registry.validateOutput("nested",JSON.readTree("{\"data\":1}"));
        assertThrows(IllegalStateException.class,()->registry.validateOutput("nested",JSON.readTree("{\"data\":\"bad\"}")));
        var invalid=new McpToolDefinition("bad","bad",JSON.readTree("{\"type\":\"not-a-type\"}"),true,(c,a)->null);
        assertThrows(IllegalArgumentException.class,()->new McpToolRegistry(List.of(()->List.of(invalid)),new Policy()));
    }
    @Test void auditIsRedactedByDefaultAndOnlyExplicitTargetsAreStored() throws Exception {
        var store=new MemoryStore();var c=new McpCredentials(store,id->ADMIN,new Policy());
        var owner=c.byId(c.create(ADMIN,"client").credential().id());
        var args=JSON.readTree("{\"id\":\"123\",\"command\":\"secret command\",\"token\":\"secret token\"}");
        var ordinary=new McpAudit(store,properties());assertTrue(ordinary.begin(owner,"tool",args).target().isEmpty());
        var custom=new McpAudit(store,properties(),(tool,a)->Map.of("id",a.path("id").asText()));
        var entry=custom.begin(owner,"tool",args);assertEquals(Map.of("id","123"),entry.target());
        custom.finish(entry,true);assertFalse(store.rows.values().toString().contains("secret"));
    }
}

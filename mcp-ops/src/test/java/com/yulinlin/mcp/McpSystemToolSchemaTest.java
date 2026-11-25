package com.yulinlin.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.yulinlin.mcp.process.*;
import com.yulinlin.mcp.store.McpAudit;
import com.yulinlin.mcp.task.McpTasks;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.mcp.transport.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpSystemToolSchemaTest extends McpTestSupport {
    final McpProperties p=properties();
    final MemoryStore store=new MemoryStore();
    final Policy policy=new Policy();
    final TokenEnv env=new TokenEnv(policy,id->ADMIN,null);
    final McpTasks tasks=mock(McpTasks.class);
    final ProcessOverviewService processes=mock(ProcessOverviewService.class);
    final ProcessControlService controls=mock(ProcessControlService.class);
    McpToolRegistry registry;
    McpCallContext context;
    String token;

    void setup() {
        p.getCommand().setEnabled(true);p.getProcess().setEnabled(true);p.getMonitor().setEnabled(true);
        p.getCommand().setDefaultTimeoutSeconds(17);
        var created=env.issue(ADMIN,"client",null);token=created.token();
        context=ctx(created.credential().id());
        registry=new McpToolRegistry(List.of(new SystemToolProvider(p,tasks,policy,processes,controls,JSON)),policy);
    }
    @Test void actualToolsListPublishesDescriptionsResultsAndConfiguredDefaults() throws Exception {
        setup();
        var mvc=MockMvcBuilders.standaloneSetup(new McpTransport(JSON,registry,env.auth,new McpAudit(store,p),p,new McpHttpGuard(p)))
            .setControllerAdvice(new McpExceptionAdvice()).build();
        var result=mvc.perform(post("/mcp").contentType("application/json").header("Authorization","Bearer "+token)
            .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.tools.length()").value(8)).andReturn();
        JsonNode tools=JSON.readTree(result.getResponse().getContentAsString()).path("result").path("tools");
        for(var tool:tools) {
            assertFalse(tool.path("description").asText().isBlank(),tool.path("name").asText());
            assertDocumented(tool.path("inputSchema"));
            assertDocumented(tool.path("outputSchema"));
            assertTrue(tool.path("outputSchema").path("properties").has("data"));
        }
        assertEquals(17,tools.get(1).path("inputSchema").path("properties").path("timeoutSeconds").path("default").asInt());
        assertEquals(16384,tools.get(4).path("inputSchema").path("properties").path("limit").path("default").asInt());
        assertTrue(tools.get(4).path("outputSchema").path("properties").path("data").path("properties").path("hasMore").path("description").asText().contains("不代表任务已结束"));
    }
    private void assertDocumented(JsonNode schema) {
        assertFalse(schema.path("description").asText().isBlank(),schema.toString());
        schema.path("properties").forEach(this::assertDocumented);
        if(schema.has("items"))assertDocumented(schema.get("items"));
    }
    @Test void realBuiltinResultSerializationMatchesEveryAdvertisedOutputSchema() throws Exception {
        setup();String id="a".repeat(32);
        var task=new McpTasks.Task(id,context.credentialId(),"echo ok","/tmp","queued",1,null,null,null,null,17,null,false,null,null);
        when(tasks.start(any(),eq("echo ok"),isNull(),eq(17),isNull())).thenReturn(task);
        when(tasks.get(id)).thenReturn(task);when(tasks.list(1)).thenReturn(List.of(task));when(tasks.cancel(id)).thenReturn(task);
        when(tasks.output(id,0,16384)).thenReturn(new McpTasks.Output("ok","b2s=",2,false,true));
        var process=new ProcessOverviewService.Row(42,"java",null,null,null,null,false,null,"protected",null,null,null,null,null,null,null,false);
        when(processes.list("",1,"memory")).thenReturn(new ProcessOverviewService.Page(List.of(process),1,1,1,1,false));
        when(controls.stop(42,"observed-start",false,true)).thenReturn(new ProcessControlService.StopResult(42,"requested",false));
        var args=new LinkedHashMap<String,String>();
        args.put("server_info","{}");args.put("command_execute","{\"command\":\"echo ok\"}");args.put("task_list","{}");
        for(var name:List.of("task_get","task_output","task_cancel"))args.put(name,"{\"id\":\""+id+"\"}");
        args.put("process_list","{}");args.put("process_stop","{\"pid\":42,\"identity\":\"observed-start\",\"confirm\":true}");
        for(var entry:args.entrySet()) {
            Object value=registry.call(context,entry.getKey(),JSON.readTree(entry.getValue()));
            var payload=JSON.createObjectNode();payload.set("data",JSON.valueToTree(value));
            registry.validateOutput(entry.getKey(),payload);
            // The transport may add a completion warning without changing the business result.
            payload.put("warning","audit incomplete");registry.validateOutput(entry.getKey(),payload);
        }
        var invalid=JSON.createObjectNode();invalid.set("data",JSON.valueToTree(task));
        ((com.fasterxml.jackson.databind.node.ObjectNode)invalid.get("data")).put("state","invented");
        assertThrows(IllegalStateException.class,()->registry.validateOutput("task_get",invalid));
        verify(tasks).start(any(),eq("echo ok"),isNull(),eq(17),isNull());
    }
    @Test void nullableAndPlatformOptionalFieldsMayBeOmitted() throws Exception {
        setup();
        registry.validateOutput("task_get",JSON.readTree("""
            {"data":{"id":"a","credentialId":"c","command":"echo","cwd":"/tmp","state":"queued","createdAt":1,"timeoutSeconds":0,"outputTruncated":false}}
            """));
        registry.validateOutput("server_info",JSON.readTree("""
            {"data":{"os":"test","arch":"test","javaVersion":"25","executionUser":"app","processors":1,"loadAverage":-1,"pid":42,"netdiskPid":42,"disks":[],"network":[]}}
            """));
    }
}

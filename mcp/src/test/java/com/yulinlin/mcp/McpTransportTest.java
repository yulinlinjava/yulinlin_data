package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.mcp.transport.*;
import com.yulinlin.security.auth.SecurityContext;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpTransportTest extends McpTestSupport {
    MockMvc mvc;
    Policy policy;
    McpCredentials credentials;
    String token;
    AtomicInteger calls;
    McpProperties properties;

    @BeforeEach void setup() throws Exception {
        properties = properties(); policy = new Policy(); calls = new AtomicInteger();
        var store = new MemoryStore();
        credentials = new McpCredentials(store, id -> ADMIN, policy);
        token = credentials.create(ADMIN, "client").token(); credentials.enable(true);
        var tool = new McpToolDefinition("echo", "echo", JSON.readTree("""
            {"type":"object","required":["n"],"properties":{"n":{"type":"integer"}},"additionalProperties":false}
            """), true, (context, args) -> {
                assertEquals(ADMIN, SecurityContext.getUser());
                calls.incrementAndGet(); return args;
            });
        var registry = new McpToolRegistry(List.of(() -> List.of(tool)), policy);
        mvc = MockMvcBuilders.standaloneSetup(new McpTransport(JSON, registry, credentials,
            new McpAudit(store, properties), properties, new McpHttpGuard(properties)))
            .setControllerAdvice(new McpExceptionAdvice()).build();
    }
    ResultActions rpc(String body) throws Exception {
        return mvc.perform(post("/mcp").contentType("application/json")
            .header("Authorization", "Bearer " + token).content(body));
    }
    @Test void callBindsIdentityOnlyForHandlerAndRejectsInvalidArguments() throws Exception {
        rpc(""" 
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"n":2}}}
            """).andExpect(status().isOk()).andExpect(jsonPath("$.result.structuredContent.data.n").value(2))
            .andExpect(header().string("Cache-Control", "no-store"));
        assertNull(SecurityContext.getUser());
        rpc("""
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"n":"2"}}}
            """).andExpect(jsonPath("$.error.code").value(-32602));
        assertEquals(1, calls.get());
    }
    @Test void permissionsAreCheckedAtListingAndInvocationAndRevocationIsImmediate() throws Exception {
        policy.invoke = false;
        rpc(""" 
            {"jsonrpc":"2.0","id":1,"method":"tools/list"}
            """).andExpect(jsonPath("$.result.tools").isEmpty());
        rpc(""" 
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"n":2}}}
            """).andExpect(status().isForbidden());
        credentials.revoke(credentials.list().getFirst().id());
        rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")
            .andExpect(status().isUnauthorized());
        assertEquals(0, calls.get());
    }
    @Test void malformedRequestsAndHostileOriginsCannotReachHandlers() throws Exception {
        rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":2,\"method\":\"ping\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(-32700));
        rpc("{} {}").andExpect(status().isBadRequest());
        mvc.perform(post("/mcp").header("Origin", "https://attacker.example")
            .header("Authorization", "Bearer " + token).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        properties.setMaxRequestBytes(2);
        rpc("{\"large\":1}").andExpect(status().isPayloadTooLarge());
        assertEquals(0, calls.get());
    }
    @Test void initializationAndNotificationsFollowStatelessProtocol() throws Exception {
        rpc("""
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
            """).andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"));
        rpc("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")
            .andExpect(status().isAccepted());
        mvc.perform(get("/mcp")).andExpect(status().isMethodNotAllowed());
    }
}

package com.yulinlin.mcp;

import com.yulinlin.mcp.management.*;
import com.yulinlin.mcp.store.McpAudit;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.token.McpTokenService.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.mcp.transport.McpHttpGuard;
import com.yulinlin.security.auth.*;
import com.yulinlin.starter.domain.R;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpManagementTest extends McpTestSupport {
    record Setup(TokenEnv env, McpManagementController controller, ISecurityManager security, McpAudit audit) { }

    Setup setup() throws Exception {
        var policy = new Policy();
        var tool = new McpToolDefinition("echo", "echo", JSON.readTree("{\"type\":\"object\"}"), true, (c, a) -> a);
        var registry = new McpToolRegistry(List.of(() -> List.of(tool)), policy);
        var env = new TokenEnv(policy, id -> new User(id), registry);
        var audit = new McpAudit(env.store, properties());
        var listener = new McpTokenAuditListener(audit);
        var service = new McpTokenService(env.tokens, id -> new User(id), policy, () -> registry, env.properties.getToken(), listener::on, env.time);
        var security = mock(ISecurityManager.class); when(security.requireUser()).thenReturn(ADMIN);
        var guard = new McpAutoConfiguration().mcpManagementGuard(new McpHttpGuard(properties()));
        var controller = new McpManagementController(service, env.auth, registry, audit, security, policy, guard);
        return new Setup(env, controller, security, audit);
    }

    static MockHttpServletRequest write() {
        var request = new MockHttpServletRequest(); request.addHeader("X-MCP-Action", "mcp-management"); return request;
    }

    @SuppressWarnings("unchecked")
    static <T> T data(R<?> result) { return (T) result.getData(); }

    @Test void tokenLifecycleThroughControllerIsAuditedWithoutSecrets() throws Exception {
        var s = setup();
        var response = new MockHttpServletResponse();
        McpTokenView.Issued issued = data(s.controller().issue(new IssueRequest("client", null, null, null, List.of("echo"), null, null, null), write(), response));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        String id = issued.credential().id();
        s.controller().update(id, new UpdateRequest("renamed", null, null, null, null, null, null), write(), new MockHttpServletResponse());
        McpTokenView.Issued rotated = data(s.controller().rotate(id, new McpManagementController.Rotate(60), write(), new MockHttpServletResponse()));
        s.controller().disable(id, write());
        s.controller().enable(id, write());
        s.controller().revoke(id, write());
        s.controller().delete(id, write());
        List<McpAudit.ManagementEntry> entries = data(s.controller().audit("manage", 1));
        assertEquals(Set.of("ISSUE", "UPDATE", "ROTATE", "DISABLE", "ENABLE", "REVOKE", "DELETE"),
                entries.stream().map(McpAudit.ManagementEntry::action).collect(java.util.stream.Collectors.toSet()));
        String stored = s.env().store.rows.values().toString();
        for (var token : List.of(issued.token(), rotated.token()))
            assertFalse(stored.contains(McpTokenCodec.parse(token).secret()));
        List<Map<String, Object>> tools = data(s.controller().tools());
        assertEquals("echo", tools.getFirst().get("name"));
        assertThrows(IllegalArgumentException.class, () -> s.controller().audit("other", 1));
    }

    @Test void writesRequireActionHeaderAndManagementDoesNotImplyToolOrUsePermission() throws Exception {
        var s = setup(); var policy = s.env().policy;
        assertThrows(McpException.class, () -> s.controller().issue(IssueRequest.of("x"), new MockHttpServletRequest(), new MockHttpServletResponse()));
        policy.use = false; policy.invoke = false;
        s.controller().enabled(new McpManagementController.Enable(true), write());
        assertTrue(s.env().auth.enabled());
        assertThrows(McpException.class, () -> s.controller().issue(IssueRequest.of("test"), write(), new MockHttpServletResponse()));
        assertThrows(McpException.class, () -> s.controller().cancel("a".repeat(32), write()));
        policy.manage = false;
        assertDoesNotThrow(() -> s.controller().state());
        assertThrows(McpException.class, () -> s.controller().enabled(new McpManagementController.Enable(false), write()));
        assertThrows(McpException.class, () -> s.controller().audit("call", 1));
        // Supplying an MCP token cannot replace requireUser() with a management identity.
        policy.manage = true; when(s.security().requireUser()).thenThrow(new LoginException("browser login required"));
        var withToken = write(); withToken.addHeader("Authorization", "Bearer ylmcp_external");
        assertThrows(LoginException.class, () -> s.controller().enabled(new McpManagementController.Enable(false), withToken));
        assertTrue(s.env().auth.enabled());
    }

    @Test void customActionHeaderAloneDoesNotOverrideOriginOrHttpsPolicy() {
        var p=properties();var guard=new McpAutoConfiguration().mcpManagementGuard(new McpHttpGuard(p));
        var request=new MockHttpServletRequest();request.addHeader("X-MCP-Action","mcp-management");
        request.addHeader("Origin","https://hostile.example");
        assertThrows(McpException.class,()->guard.checkWrite(request));
        request.removeHeader("Origin");guard.checkWrite(request);
        p.setAllowInsecure(false);request.setRemoteAddr("192.0.2.10");request.setServerName("server.example");
        assertThrows(McpException.class,()->guard.checkWrite(request));
        request.setSecure(true);guard.checkWrite(request);
        request.removeHeader("X-MCP-Action");assertThrows(McpException.class,()->guard.checkWrite(request));
    }
}

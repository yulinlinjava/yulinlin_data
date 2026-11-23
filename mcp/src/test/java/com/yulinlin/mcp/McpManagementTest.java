package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.management.*;
import com.yulinlin.mcp.store.McpAudit;
import com.yulinlin.mcp.task.McpTasks;
import com.yulinlin.mcp.transport.McpHttpGuard;
import com.yulinlin.security.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpManagementTest extends McpTestSupport {
    @Test void managementUsesWebIdentityAndDoesNotImplyToolOrUsePermission() {
        var policy=new Policy();var store=new MemoryStore();var credentials=new McpCredentials(store,id->ADMIN,policy);
        var security=mock(ISecurityManager.class);when(security.requireUser()).thenReturn(ADMIN);
        var http=new McpHttpGuard(properties());
        var guard=new McpAutoConfiguration().mcpManagementGuard(http);
        var controller=new McpManagementController(credentials,new DefaultListableBeanFactory().getBeanProvider(McpTasks.class),
            new McpAudit(store,properties()),security,policy,guard);
        var request=new MockHttpServletRequest();request.addHeader("X-MCP-Action","mcp-management");
        policy.use=false;policy.invoke=false;
        controller.enabled(new McpManagementController.Enable(true),request);
        assertTrue(credentials.enabled());
        assertThrows(McpException.class,()->controller.create(new McpManagementController.Create("test"),request,new MockHttpServletResponse()));
        assertThrows(McpException.class,()->controller.cancel("a".repeat(32),request));
        policy.manage=false;
        assertThrows(McpException.class,()->controller.state(new MockHttpServletResponse()));
        // Supplying an external token cannot replace requireUser() with a management identity.
        policy.manage=true;when(security.requireUser()).thenThrow(new LoginException("browser login required"));
        assertThrows(LoginException.class,()->controller.enabled(new McpManagementController.Enable(false),request("ylmcp_external")));
        assertTrue(credentials.enabled());
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

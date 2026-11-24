package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.mcp.transport.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpServiceExtensionTest extends McpTestSupport {
    /** 例如网关签名、OAuth：只识别自己的请求头，其余交给下一个认证器。 */
    static class GatewayAuthenticator implements McpAuthenticator {
        public String type() { return "gateway"; }
        public McpPrincipal authenticate(HttpServletRequest request) {
            String user = request.getHeader("X-Gateway-User");
            return user == null ? null : new McpPrincipal("gateway", "gw-" + user, "gateway", user, List.of("@readonly"), Map.of("tenant", "t1"));
        }
    }

    @Test void eachCustomBeanIndependentlyReplacesOnlyItsDefault() {
        var config=new McpConfigurationTest();var tokens=new MemoryTokenStore();
        config.enabledWithoutTokenStore().withBean(McpAccessPolicy.class,Policy::new).withBean(McpTokenStore.class,()->tokens).run(c->{
            assertNull(c.getStartupFailure());
            assertSame(tokens,c.getBean(McpTokenStore.class));assertInstanceOf(McpAudit.class,c.getBean(McpAuditService.class));
            c.getBean(McpAuthentication.class).enable(true);
            var issued=c.getBean(McpTokenService.class).issue(McpTokenService.Actor.of(ADMIN),McpTokenService.IssueRequest.of("x"));
            assertNotNull(tokens.findById(issued.credential().id()));
            assertEquals(issued.credential().id(),c.getBean(McpAuthentication.class).authenticate(request(issued.token())).credentialId());
        });
        var audit=mock(McpAuditService.class);
        config.enabled().withBean(McpAccessPolicy.class,Policy::new).withBean(McpAuditService.class,()->audit).run(c->{
            assertNull(c.getStartupFailure());assertTrue(c.getBeansOfType(McpAudit.class).isEmpty());
            assertSame(audit,c.getBean(McpAuditService.class));
        });
    }

    @Test void additionalAuthenticatorsRunAfterTokensAndKeepScopeAndAttributes() {
        var config=new McpConfigurationTest();
        config.enabled().withBean(McpAccessPolicy.class,Policy::new).withBean(GatewayAuthenticator.class,GatewayAuthenticator::new).run(c->{
            assertNull(c.getStartupFailure());
            var auth=c.getBean(McpAuthentication.class);auth.enable(true);
            var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("X-Gateway-User","admin");
            var principal=auth.authenticate(request);
            assertEquals("gateway",principal.type());assertEquals("t1",principal.attribute("tenant"));
            assertSame(principal,auth.refresh(principal));
            var user=auth.requireUser(principal);
            auth.requireTool(principal,user,"read_tool",true);
            assertThrows(McpException.class,()->auth.requireTool(principal,user,"write_tool",false));
            assertThrows(McpException.class,()->auth.authenticate(new org.springframework.mock.web.MockHttpServletRequest()));
        });
    }

    @Test void customAuditReceivesCallsAndFailuresKeepExistingExecutionSemantics() throws Exception {
        var policy=new Policy();var count=new AtomicInteger();
        var tool=new McpToolDefinition("example","example",JSON.readTree("{\"type\":\"object\"}"),true,(ctx,args)->{count.incrementAndGet();return Map.of("ok",true);});
        var registry=new McpToolRegistry(List.of(()->List.of(tool)),policy);var audit=mock(McpAuditService.class);
        var env=new TokenEnv(policy,id->ADMIN,registry);var created=env.issue(ADMIN,"audit",null);
        var entry=new McpAudit.Entry("audit-id",created.credential().id(),"audit","admin","example",Map.of(),1000,null,"started");
        when(audit.begin(any(),eq("example"),any())).thenReturn(entry);
        var transport=new McpTransport(JSON,registry,env.auth,audit,properties(),new McpHttpGuard(properties()));
        var req=request(created.token());req.setContentType("application/json");req.setContent("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"example\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        transport.post(req,new org.springframework.mock.web.MockHttpServletResponse());verify(audit).finish(entry,true);assertEquals(1,count.get());
        verify(audit).begin(argThat(p->p.credentialId().equals(created.credential().id())&&p.accountId().equals("admin")),eq("example"),any());
        when(audit.begin(any(),anyString(),any())).thenThrow(new IllegalStateException("offline"));
        req.setContent(req.getContentAsByteArray());
        assertEquals(500,transport.post(req,new org.springframework.mock.web.MockHttpServletResponse()).getStatusCode().value());assertEquals(1,count.get());
    }
}

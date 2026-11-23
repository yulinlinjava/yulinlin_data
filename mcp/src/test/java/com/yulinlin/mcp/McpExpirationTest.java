package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.McpCredentials;
import com.yulinlin.mcp.management.McpManagementController;
import com.yulinlin.mcp.store.McpAudit;
import com.yulinlin.mcp.task.McpTasks;
import com.yulinlin.mcp.transport.McpExceptionAdvice;
import com.yulinlin.security.auth.ISecurityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpExpirationTest extends McpTestSupport {
    static class Time extends Clock {
        volatile long now = 1000;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(now);}
        public long millis(){return now;}
    }
    @Test void nullZeroAndOmittedDeadlinesStayUnlimitedButCanBeRevoked() {
        var time=new Time();var store=new MemoryStore();var c=new McpCredentials(store,id->ADMIN,new Policy(),time);
        c.enable(true);
        var omitted=c.create(ADMIN,"omitted");var nil=c.create(ADMIN,"null",null);var zero=c.create(ADMIN,"zero",0L);
        time.now=Long.MAX_VALUE;
        for(var created:java.util.List.of(omitted,nil,zero)) {
            assertEquals(0,created.credential().expiresAt());
            assertEquals(created.credential().id(),c.authenticate(request(created.token())).id());
            c.revoke(created.credential().id());
            assertFalse(c.list().stream().filter(i->i.id().equals(created.credential().id())).findFirst().orElseThrow().active());
            assertThrows(McpException.class,()->c.authenticate(request(created.token())));
        }
    }
    @Test void expiryIsAbsoluteAndSurvivesRestartWithExactBoundary() {
        var time=new Time();var store=new MemoryStore();var c=new McpCredentials(store,id->ADMIN,new Policy(),time);c.enable(true);
        var created=c.create(ADMIN,"temporary",2000L);
        var snapshot=c.authenticate(request(created.token()));
        time.now=1999;assertNotNull(c.authenticate(request(created.token())));
        assertEquals(2000,c.list().getFirst().expiresAt());assertTrue(c.list().getFirst().active());
        var restarted=new McpCredentials(store,id->ADMIN,new Policy(),time);
        time.now=2000;
        assertEquals(401,assertThrows(McpException.class,()->restarted.authenticate(request(created.token()))).status());
        assertThrows(McpException.class,()->c.requireValid(snapshot));
        assertEquals(2000,restarted.list().getFirst().expiresAt());assertFalse(restarted.list().getFirst().active());
        time.now=2001;assertThrows(McpException.class,()->restarted.authenticate(request(created.token())));
        restarted.revoke(created.credential().id());assertEquals(2000,restarted.byId(snapshot.id()).expiresAt());
    }
    @Test void invalidCreationDeadlinesDoNotPersistCredentials() {
        var time=new Time();var store=new MemoryStore();var c=new McpCredentials(store,id->ADMIN,new Policy(),time);
        for(long deadline:new long[]{-1,Long.MIN_VALUE,999,1000})
            assertThrows(IllegalArgumentException.class,()->c.create(ADMIN,"invalid",deadline));
        assertEquals(0,store.count("credential"));
        assertEquals(1001,c.create(ADMIN,"next millisecond",1001L).credential().expiresAt());
    }
    @Test void legacyNullMissingZeroAndPositiveDeadlinesUseTheSameRules() {
        for(String field:new String[]{"",",\"expiresAt\":null",",\"expiresAt\":0",",\"expiresAt\":1999",",\"expiresAt\":2000",",\"expiresAt\":2001",",\"expiresAt\":-1"}) {
            var time=new Time();time.now=2000;var store=new MemoryStore();var c=new McpCredentials(store,id->ADMIN,new Policy(),time);c.enable(true);
            String token="ndmcp_legacy",digest=McpCredentials.hash(token);
            String payload="{\"id\":\"legacy\",\"name\":\"legacy\",\"digest\":\""+digest+"\",\"userId\":\"admin\",\"createdAt\":1,\"revoked\":false"+field+"}";
            store.rows.put("credential-"+digest,new MemoryStore.Row("credential",payload,1));
            boolean valid=field.isEmpty()||field.endsWith("null")||field.endsWith(":0")||field.endsWith(":2001");
            assertEquals(valid,c.list().getFirst().active(),field);
            if(valid)assertNotNull(c.authenticate(request(token)),field);
            else assertThrows(McpException.class,()->c.authenticate(request(token)),field);
        }
    }
    @Test void managementJsonAcceptsOptionalDeadlineAndReportsExpiredStatus() throws Exception {
        var time=new Time();var store=new MemoryStore();var c=new McpCredentials(store,id->ADMIN,new Policy(),time);c.enable(true);
        var security=mock(ISecurityManager.class);when(security.requireUser()).thenReturn(ADMIN);
        var controller=new McpManagementController(c,new DefaultListableBeanFactory().getBeanProvider(McpTasks.class),
            new McpAudit(store,properties()),security,new Policy(),request->{});
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new McpExceptionAdvice()).build();
        for(String field:new String[]{"",",\"expiresAt\":null",",\"expiresAt\":0"})
            mvc.perform(post("/api/mcp/credentials").contentType("application/json").content("{\"name\":\"forever\""+field+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.credential.expiresAt").value(0));
        var result=mvc.perform(post("/api/mcp/credentials").contentType("application/json")
            .content("{\"name\":\"temporary\",\"expiresAt\":2000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.credential.expiresAt").value(2000)).andReturn();
        String token=JSON.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
        assertNotNull(c.authenticate(request(token)));
        for(long invalid:new long[]{-1,999,1000})
            mvc.perform(post("/api/mcp/credentials").contentType("application/json").content("{\"name\":\"invalid\",\"expiresAt\":"+invalid+"}"))
                .andExpect(status().isBadRequest());
        time.now=2000;
        mvc.perform(get("/api/mcp")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.credentials[0].expiresAt").value(2000))
            .andExpect(jsonPath("$.data.credentials[0].active").value(false));
        assertThrows(McpException.class,()->c.authenticate(request(token)));
    }
}

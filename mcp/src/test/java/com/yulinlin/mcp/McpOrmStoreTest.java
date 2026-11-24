package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.McpAuthentication;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.token.McpTokenService.*;
import com.yulinlin.jdbc.h2.H2Session;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.yulinlin.mcp.McpTestSupport.*;

class McpOrmStoreTest extends OrmTestSupport {
    @BeforeEach void database() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:mcp_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var session = new H2Session(ds); session.setCoderManager(new H2CoderManager()); install(session);
    }
    static McpProperties props(String app) { var p=properties();p.setApplicationId(app);p.setGroup("mcp-test");return p; }
    OrmMcpStore store(String app) { var store=new OrmMcpStore(JSON,props(app));store.initialize();return store; }
    OrmMcpTokenStore tokens(String app) { var store=new OrmMcpTokenStore(JSON,props(app));store.initialize();return store; }
    McpTokenService service(McpTokenStore store) {
        var token=new McpProperties.Token();token.setCacheSeconds(0);
        return new McpTokenService(store,id->new User(id),new Policy(),()->null,token,e->{},java.time.Clock.systemUTC());
    }

    @Test void tokenRoundTripsAllFieldsAndAuthenticatesAfterRestart() {
        var service=service(tokens("a"));
        var issued=service.issue(Actor.of(ADMIN),new IssueRequest("client","备注","reader",System.currentTimeMillis()+600_000,
                List.of("echo","netdisk.*"),Map.of("tenant","t1"),List.of("10.0.0.0/8"),5));
        var restarted=tokens("a");
        var stored=restarted.findByKeyId(issued.credential().keyId());
        assertEquals(issued.credential().id(),stored.id());
        assertEquals("备注",stored.remark());assertEquals("reader",stored.ownerId());assertEquals("admin",stored.issuerId());
        assertEquals(List.of("echo","netdisk.*"),stored.scope());assertEquals(Map.of("tenant","t1"),stored.attributes());
        assertEquals(List.of("10.0.0.0/8"),stored.allowedIps());assertEquals(5,stored.rateLimit());assertEquals(1,stored.version());
        assertEquals(McpTokenCodec.hash(McpTokenCodec.parse(issued.token()).secret()),stored.tokenHash());
        assertEquals(stored,restarted.findById(stored.id()));
        var records=store("a");
        var auth=new McpAuthentication(List.of(new TokenAuthenticator(service(restarted))),records,id->new User(id),new Policy());
        auth.enable(true);
        var request=request(issued.token());request.setRemoteAddr("10.1.1.1");
        assertEquals(stored.id(),auth.authenticate(request).credentialId());
    }

    @Test void applicationsAreIsolated() {
        var issued=service(tokens("a")).issue(Actor.of(ADMIN),IssueRequest.of("a"));
        var other=tokens("b");
        assertNull(other.findByKeyId(issued.credential().keyId()));
        assertNull(other.findById(issued.credential().id()));
        assertEquals(0,other.count(new McpTokenQuery(null,null,null,1,20,0)));
        store("a").save("settings","settings",new McpAuthentication.Settings(true));
        assertNull(store("b").get("settings",McpAuthentication.Settings.class));
    }

    @Test void versionConflictRejectsStaleWritesAndPreservesUsage() throws Exception {
        var a=tokens("cas");var b=tokens("cas");
        var id=service(a).issue(Actor.of(ADMIN),IssueRequest.of("old")).credential().id();
        var base=a.findById(id);
        a.markUsed(id,100_000,"10.0.0.1");
        assertTrue(a.update(base.toBuilder().name("first").version(2).build(),1));
        assertFalse(b.update(base.toBuilder().name("stale").version(2).build(),1));
        var after=b.findById(id);
        assertEquals("first",after.name());assertEquals(2,after.version());
        assertEquals(100_000,after.lastUsedAt());assertEquals("10.0.0.1",after.lastUsedIp());
        var barrier=new CountDownLatch(1);var wins=new AtomicInteger();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<?>>();
            for(var store:List.of(a,b))futures.add(pool.submit(()->{barrier.await();if(store.update(after.toBuilder().remark("x").version(3).build(),2))wins.incrementAndGet();return null;}));
            barrier.countDown();for(var f:futures)f.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,wins.get());
    }

    @Test void usageIsMonotonicAndThrottledPerMinute() {
        var a=tokens("usage");var id=service(a).issue(Actor.of(ADMIN),IssueRequest.of("u")).credential().id();
        a.markUsed(id,100_000,"1");a.markUsed(id,130_000,"2");
        assertEquals("1",a.findById(id).lastUsedIp());
        a.markUsed(id,161_000,"3");a.markUsed(id,50_000,"4");
        assertEquals(161_000,a.findById(id).lastUsedAt());assertEquals("3",a.findById(id).lastUsedIp());
    }

    @Test void queriesFilterByOwnerStatusKeywordAndPage() {
        var store=tokens("query");var service=service(store);long now=System.currentTimeMillis();
        service.issue(Actor.of(ADMIN),IssueRequest.of("alpha"));
        var beta=service.issue(Actor.of(ADMIN),new IssueRequest("beta",null,"reader",null,null,null,null,null)).credential();
        var gamma=service.issue(Actor.of(ADMIN),new IssueRequest("gamma",null,null,now+60_000,null,null,null,null)).credential();
        service.disable(Actor.of(ADMIN),beta.id());
        assertEquals(3,store.count(new McpTokenQuery(null,null,null,1,20,now)));
        assertEquals(1,store.count(new McpTokenQuery("reader",null,null,1,20,now)));
        assertEquals(1,store.count(new McpTokenQuery(null,McpTokenStatus.DISABLED,null,1,20,now)));
        assertEquals(2,store.count(new McpTokenQuery(null,McpTokenStatus.ACTIVE,null,1,20,now)));
        assertEquals(1,store.count(new McpTokenQuery(null,McpTokenStatus.EXPIRED,null,1,20,now+120_000)));
        assertEquals(List.of(gamma.id()),store.list(new McpTokenQuery(null,McpTokenStatus.EXPIRED,null,1,20,now+120_000)).stream().map(McpToken::id).toList());
        assertEquals(1,store.count(new McpTokenQuery(null,null,"amm",1,20,now)));
        assertEquals(1,store.count(new McpTokenQuery(null,null,beta.keyId(),1,20,now)));
        assertEquals(2,store.list(new McpTokenQuery(null,null,null,1,2,now)).size());
        assertEquals(1,store.list(new McpTokenQuery(null,null,null,2,2,now)).size());
        assertEquals(1,store.countUnrevoked("reader"));
        service.revoke(Actor.of(ADMIN),beta.id());
        assertEquals(0,store.countUnrevoked("reader"));
        service.delete(Actor.of(ADMIN),beta.id());
        assertNull(store.findById(beta.id()));
    }

    @Test void atomicCreatePreventsDuplicateTaskAdmissionAcrossStores() throws Exception {
        var a=store("shared");var b=store("shared");var barrier=new CountDownLatch(1);
        var winners=new AtomicInteger();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=new ArrayList<Future<?>>();
            for(var current:List.of(a,b)) futures.add(pool.submit(()->{
                barrier.await();
                try{current.create("task-same","task",Map.of("value",1));winners.incrementAndGet();}
                catch(RuntimeException expectedDuplicate){assertNotNull(current.get("task-same",Map.class));}
                return null;
            }));
            barrier.countDown();for(var future:futures)future.get(5,TimeUnit.SECONDS);
        }
        assertEquals(1,winners.get());assertEquals(1,a.count("task"));
    }
}

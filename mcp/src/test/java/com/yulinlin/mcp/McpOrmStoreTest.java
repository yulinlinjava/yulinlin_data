package com.yulinlin.mcp;

import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.auth.McpCredentials;
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
    OrmMcpStore store(String app) {
        var p=properties();p.setApplicationId(app);p.setGroup("mcp-test");
        var store=new OrmMcpStore(JSON,p);store.initialize();return store;
    }
    @Test void schemaNamespacesAndPersistentRevocationSurviveServiceRecreation() {
        var a=store("a");var b=store("b");
        var credentials=new McpCredentials(a,id->ADMIN,new Policy());
        credentials.enable(true);var created=credentials.create(ADMIN,"local");
        assertEquals(0,b.count("credential"));assertNull(b.get("settings",McpCredentials.Settings.class));
        var restarted=new McpCredentials(store("a"),id->ADMIN,new Policy());
        assertEquals(created.credential().id(),restarted.authenticate(request(created.token())).id());
        restarted.revoke(created.credential().id());
        assertThrows(McpException.class,()->credentials.authenticate(request(created.token())));
        assertEquals(1,a.list("credential",1,10,McpCredentials.Credential.class).size());
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

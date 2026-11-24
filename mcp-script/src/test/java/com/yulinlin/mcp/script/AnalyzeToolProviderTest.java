package com.yulinlin.mcp.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.H2Session;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.McpProperties;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.data.McpDataService;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.mcp.tool.McpCallContext;
import com.yulinlin.security.auth.IUserDetails;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzeToolProviderTest {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String G = "mcp-test";

    record User(String getUserId) implements IUserDetails {
        public String getUsername() { return getUserId; }
        public Set<String> getRoles() { return Set.of(); }
        public Set<String> getPermissions() { return Set.of(); }
        public Object getAttribute(String name) { return null; }
    }

    static class Policy implements McpAccessPolicy {
        volatile boolean invoke = true;
        public boolean canUse(IUserDetails u) { return true; }
        public boolean canManage(IUserDetails u) { return false; }
        public boolean canInvoke(IUserDetails u, String tool) { return invoke; }
    }

    static class MemoryStore implements McpStore {
        final ConcurrentMap<String, String> rows = new ConcurrentHashMap<>();
        final ConcurrentMap<String, String> kinds = new ConcurrentHashMap<>();
        public <T> T get(String key, Class<T> type) {
            try { var v = rows.get(key); return v == null ? null : JSON.readValue(v, type); } catch (Exception e) { throw new IllegalStateException(e); }
        }
        public <T> List<T> list(String kind, int page, int size, Class<T> type) {
            return kinds.entrySet().stream().filter(e -> e.getValue().equals(kind)).skip((long) (page - 1) * size).limit(size).map(e -> get(e.getKey(), type)).toList();
        }
        public void create(String key, String kind, Object value) { if (rows.containsKey(key)) throw new IllegalStateException("duplicate"); save(key, kind, value); }
        public void save(String key, String kind, Object value) {
            try { rows.put(key, JSON.writeValueAsString(value)); kinds.put(key, kind); } catch (Exception e) { throw new IllegalStateException(e); }
        }
        public void delete(String key) { rows.remove(key); kinds.remove(key); }
        public long count(String kind) { return kinds.values().stream().filter(kind::equals).count(); }
    }

    final RouteSession previous = SessionUtil.route();
    JdbcDataSource ds;
    H2Session session;
    McpDataService data;
    ScriptSandbox sandbox;
    Policy policy;
    AnalyzeToolProvider provider;

    @BeforeEach void setup() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:analyze_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        session = new H2Session(ds);
        session.setCoderManager(new H2CoderManager());
        session.setGroup(G);
        session.setProxyService(EntityProxyService.newInstance());
        var route = RouteSession.builder().proxyService(EntityProxyService.newInstance()).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.registerSession(session);
        route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class, DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
        new SessionUtil(route);
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            s.execute("create table users(id varchar(8) primary key, level varchar(8))");
            s.execute("create table orders(user_id varchar(8), amount decimal(10,2))");
            s.execute("insert into users values ('u1','normal'),('u2','normal'),('u3','gold')");
            s.execute("insert into orders values ('u1',3000),('u1',500),('u2',100),('u3',50)");
        }
        var props = new McpProperties();
        props.setApplicationId("test");
        props.getData().setWrite(List.of(G));
        data = new McpDataService(props, new MemoryStore(), JSON, () -> null);
        data.initialize();
        sandbox = new ScriptSandbox(JSON);
        policy = new Policy();
        provider = new AnalyzeToolProvider(data, sandbox, policy, JSON);
    }

    @AfterEach void teardown() {
        sandbox.close();
        session.getThreadPoolExecutor().shutdownNow();
        new SessionUtil(previous);
    }

    static McpCallContext ctx(List<String> scope) {
        var principal = new McpPrincipal("token", "cred-1", "test", "u-admin", scope, null);
        return new McpCallContext("test", principal, new User("u-admin"), "req", "");
    }

    Object call(List<String> scope, String json) throws Exception {
        var tool = provider.tools().iterator().next();
        return tool.handler().execute(ctx(scope), JSON.readTree(json));
    }

    int count(String sqlText) throws Exception {
        try (var c = ds.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sqlText)) { rs.next(); return rs.getInt(1); }
    }

    static final String LEVEL_SCRIPT = """
            const rows = query('mcp-test', `select u.id, u.level, coalesce(sum(o.amount), 0) as spent
              from users u left join orders o on o.user_id = u.id group by u.id, u.level order by u.id`);
            const levelFor = s => s >= 2000 ? 'silver' : 'normal';
            const changes = rows.filter(r => levelFor(r.spent) !== r.level && r.level !== 'gold')
                                .map(r => ({id: r.id, from: r.level, to: levelFor(r.spent)}));
            for (const c of changes) execute('mcp-test', 'update users set level = #{to} where id = #{id} and level = #{from}', c);
            return {changed: changes.length, ids: changes.map(c => c.id)};
            """;

    String args(String extra) throws Exception {
        return "{\"script\":" + JSON.writeValueAsString(LEVEL_SCRIPT) + extra + "}";
    }

    @Test @SuppressWarnings("unchecked") void readOnlyAnalysisReturnsOnlyTheResult() throws Exception {
        var out = (Map<String, Object>) call(List.of("data.analyze"), """
                {"script": "const r = query('mcp-test', 'select user_id, sum(amount) as total from orders group by user_id order by total desc'); log('groups', r.length); return r[0];"}""");
        var result = (JsonNode) out.get("result");
        assertEquals("u1", result.get("user_id").asText());
        assertEquals(3500, result.get("total").asInt());
        assertEquals(List.of("groups 3"), out.get("logs"));
        assertEquals(1, out.get("queries"));
        assertThrows(IllegalArgumentException.class, () -> call(List.of("data.analyze"), args("")), "execute without writeGroup");
    }

    @Test @SuppressWarnings("unchecked") void writeFlowPreviewThenCommitWithExpectation() throws Exception {
        var scope = List.of("data.analyze", "data.execute");
        assertThrows(IllegalArgumentException.class, () -> call(scope, args(",\"writeGroup\":\"mcp-test\"")), "dryRun must be explicit");
        var preview = (Map<String, Object>) call(scope, args(",\"writeGroup\":\"mcp-test\",\"dryRun\":true"));
        assertEquals(1L, preview.get("totalAffectedRows"));
        assertEquals("u1", ((JsonNode) preview.get("result")).get("ids").get(0).asText());
        assertEquals(0, count("select count(*) from users where level = 'silver'"), "preview rolled back");
        var e = assertThrows(IllegalArgumentException.class, () -> call(scope, args(",\"writeGroup\":\"mcp-test\",\"dryRun\":false,\"expectAffectedRows\":2")));
        assertTrue(e.getMessage().contains("预期影响 2 行"));
        var done = (Map<String, Object>) call(scope, args(",\"writeGroup\":\"mcp-test\",\"dryRun\":false,\"expectAffectedRows\":1,\"requestId\":\"lv-1\""));
        assertEquals(true, done.get("committed"));
        assertEquals(1, count("select count(*) from users where level = 'silver'"));
        var replay = (Map<String, Object>) call(scope, args(",\"writeGroup\":\"mcp-test\",\"dryRun\":false,\"expectAffectedRows\":1,\"requestId\":\"lv-1\""));
        assertEquals(true, replay.get("replayed"));
    }

    @Test void writingRequiresDataExecutePermission() throws Exception {
        var noExecute = assertThrows(McpException.class, () -> call(List.of("data.analyze"), args(",\"writeGroup\":\"mcp-test\",\"dryRun\":true")));
        assertEquals(403, noExecute.status());
        policy.invoke = false;
        assertEquals(403, assertThrows(McpException.class,
                () -> call(List.of("data.analyze", "data.execute"), args(",\"writeGroup\":\"mcp-test\",\"dryRun\":true"))).status());
    }

    @Test void scriptErrorRollsBackEarlierWrites() throws Exception {
        var e = assertThrows(IllegalArgumentException.class, () -> call(List.of("*"), """
                {"script": "execute('mcp-test', 'update users set level = #{l} where id = #{id}', {l: 'x', id: 'u1'}); throw new Error('人工放弃');",
                 "writeGroup": "mcp-test", "dryRun": false, "expectAffectedRows": -1}"""));
        assertTrue(e.getMessage().contains("人工放弃"));
        assertEquals(0, count("select count(*) from users where level = 'x'"));
    }

    @Test void toolIsNotReadOnlyWhenWriteGroupsExist() {
        var tool = provider.tools().iterator().next();
        assertEquals(AnalyzeToolProvider.NAME, tool.name());
        assertFalse(tool.readOnly());
        assertTrue(tool.inputSchema().path("properties").has("writeGroup"));
        assertTrue(tool.description().contains("query(group, command, params)"));
    }
}

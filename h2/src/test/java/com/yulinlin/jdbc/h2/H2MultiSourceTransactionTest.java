package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.sql.Connection;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 一个事务访问多个数据源组：JDBC 组各自登记、依次提交、一起回滚；不支持事务的数据源不登记、立即执行。 */
@ResourceLock("SessionUtil.route")
class H2MultiSourceTransactionTest {
    /** 模拟 Elasticsearch：不支持事务，写入立即生效。 */
    static final class NonTransactionalSession extends H2Session {
        NonTransactionalSession(JdbcDataSource source) { super(source); }
        @Override public boolean supportsTransactions() { return false; }
    }

    /** 提交时失败，用来验证前面已提交的数据源不会被撤销、后面的会回滚。 */
    static final class FailingCommitSession extends H2Session {
        FailingCommitSession(JdbcDataSource source) { super(source); }
        @Override public void commitTransaction() {
            rollbackTransaction();
            throw new IllegalStateException("commit failed");
        }
    }

    final RouteSession previous = SessionUtil.route();
    final List<H2Session> sessions = new ArrayList<>();
    final Map<String, Connection> anchors = new HashMap<>();
    RouteSession route;

    JdbcDataSource source(String group) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:multi_" + group + "_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        var anchor = source.getConnection();
        try (var s = anchor.createStatement()) { s.execute("create table t(id int primary key)"); }
        anchors.put(group, anchor);
        return source;
    }

    void register(H2Session session, String group) {
        session.setGroup(group);
        session.setCoderManager(new H2CoderManager());
        session.setProxyService(EntityProxyService.newInstance());
        route.registerSession(session);
        sessions.add(session);
    }

    @BeforeEach void setup() throws Exception {
        route = RouteSession.builder().proxyService(EntityProxyService.newInstance()).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
        register(new H2Session(source("a")), "a");
        register(new H2Session(source("b")), "b");
        register(new NonTransactionalSession(source("es")), "es");
        register(new FailingCommitSession(source("bad")), "bad");
        new SessionUtil(route);
    }

    @AfterEach void close() throws Exception {
        try { while (route.isOpenTransaction()) route.rollbackTransaction(); }
        finally {
            new SessionUtil(previous);
            sessions.forEach(s -> s.getThreadPoolExecutor().shutdownNow());
            for (var anchor : anchors.values()) anchor.close();
        }
    }

    void insert(String group, int id) {
        var request = ExecuteRequest.newInstance("insert into t(id) values (#{id})", Map.of("id", id));
        request.setSession(group);
        request.execute();
    }

    int count(String group) throws Exception {
        try (var s = anchors.get(group).createStatement(); var rows = s.executeQuery("select count(*) from t")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test void commitsEveryEnlistedSource() throws Exception {
        route.transaction(() -> { insert("a", 1); insert("b", 1); insert("a", 2); return null; });
        assertEquals(2, count("a"));
        assertEquals(1, count("b"));
    }

    @Test void rollbackUndoesEveryTransactionalSourceButNotNonTransactional() throws Exception {
        assertThrows(IllegalStateException.class, () -> route.transaction(() -> {
            insert("a", 1);
            insert("b", 1);
            insert("es", 1);
            throw new IllegalStateException("abort");
        }));
        assertEquals(0, count("a"));
        assertEquals(0, count("b"));
        assertEquals(1, count("es"), "不支持事务的数据源立即执行，回滚不撤销");
    }

    @Test void nestedRollbackRestoresSavepointsOnAllSources() throws Exception {
        route.transaction(() -> {
            insert("a", 1);
            assertThrows(IllegalStateException.class, () -> route.transaction(() -> {
                insert("a", 2);
                insert("b", 2);   // 在内层首次登记
                throw new IllegalStateException("inner");
            }));
            insert("b", 3);
            return null;
        });
        assertEquals(1, count("a"));
        assertEquals(1, count("b"));
    }

    @Test void commitFailureKeepsEarlierCommitsAndRollsBackTheRest() throws Exception {
        var error = assertThrows(IllegalStateException.class, () -> route.transaction(() -> {
            insert("a", 1);
            insert("bad", 1);
            insert("b", 1);
            return null;
        }));
        assertEquals("commit failed", error.getMessage());
        assertTrue(Arrays.stream(error.getSuppressed()).anyMatch(s -> s.getMessage().contains("[a] 已提交")));
        assertEquals(1, count("a"));
        assertEquals(0, count("bad"));
        assertEquals(0, count("b"));
        assertFalse(route.isOpenTransaction());
    }
}

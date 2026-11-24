package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
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
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** RouteSession.transaction(callable, timeout)：剩余时间作为每条语句的查询超时，用完后语句失败、事务回滚。 */
@ResourceLock("SessionUtil.route")
class H2TransactionTimeoutTest {
    /** 记录每条语句实际设置的查询超时（秒），0 表示未设置。 */
    static final class RecordingSession extends H2Session {
        final List<Integer> timeouts = new ArrayList<>();
        RecordingSession(JdbcDataSource source) { super(source); }
        @Override protected void applyTransactionTimeout(Statement statement) throws SQLException {
            super.applyTransactionTimeout(statement);
            timeouts.add(statement.getQueryTimeout());
        }
    }

    final RouteSession previous = SessionUtil.route();
    RecordingSession session;
    RouteSession route;
    java.sql.Connection anchor;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:tx_timeout_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        anchor = source.getConnection();
        try (var s = anchor.createStatement()) { s.execute("create table t(id int primary key)"); }
        session = new RecordingSession(source);
        session.setGroup("h2");
        session.setCoderManager(new H2CoderManager());
        session.setProxyService(EntityProxyService.newInstance());
        route = RouteSession.builder().proxyService(EntityProxyService.newInstance()).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.registerSession(session);
        route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
        new SessionUtil(route);
    }

    @AfterEach void close() throws Exception {
        try { if (route.isOpenTransaction()) route.rollbackTransaction(); }
        finally {
            new SessionUtil(previous);
            session.getThreadPoolExecutor().shutdownNow();
            anchor.close();
        }
    }

    void insert(int id) {
        var request = ExecuteRequest.newInstance("insert into t(id) values (#{id})", Map.of("id", id));
        request.setSession("h2");
        request.execute();
    }

    int count() throws SQLException {
        try (var s = anchor.createStatement(); var rows = s.executeQuery("select count(*) from t")) { rows.next(); return rows.getInt(1); }
    }

    static boolean causedByTimeout(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause()) if (e instanceof SQLTimeoutException) return true;
        return false;
    }

    @Test void statementsInsideATimedTransactionGetTheRemainingTime() throws Exception {
        route.transaction(() -> { insert(1); return null; }, Duration.ofSeconds(10));
        int applied = session.timeouts.getLast();
        assertTrue(applied >= 1 && applied <= 10, "applied=" + applied);
        assertEquals(1, count());
    }

    @Test void untimedTransactionsAndPlainStatementsAreUnaffected() throws Exception {
        route.transaction(() -> { insert(1); return null; });
        insert(2);
        var query = QueryRequest.newInstance("select id from t", Map.of(), Map.class);
        query.setSession("h2");
        query.selectList();
        assertTrue(session.timeouts.stream().allMatch(t -> t == 0), session.timeouts.toString());
    }

    @Test void expiredTimeoutFailsTheNextStatementAndRollsBack() throws Exception {
        var error = assertThrows(Exception.class, () -> route.transaction(() -> {
            insert(1);
            Thread.sleep(300);
            insert(2);
            return null;
        }, Duration.ofMillis(100)));
        assertTrue(causedByTimeout(error), error.toString());
        assertEquals(0, count(), "the first insert is rolled back too");
        assertFalse(route.isOpenTransaction());
    }

    @Test void shorterInnerTimeoutOnlyAppliesInsideTheInnerScope() throws Exception {
        route.transaction(() -> {
            route.transaction(() -> { insert(1); return null; }, Duration.ofSeconds(2));
            int inner = session.timeouts.getLast();
            insert(2);
            int outer = session.timeouts.getLast();
            assertTrue(inner <= 2, "inner=" + inner);
            assertTrue(outer > 2, "outer=" + outer);
            return null;
        }, Duration.ofSeconds(60));
        assertEquals(2, count());
    }

    @Test void nonPositiveTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> route.transaction(() -> null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> route.transaction(() -> null, Duration.ofSeconds(-1)));
        assertFalse(route.isOpenTransaction());
    }
}

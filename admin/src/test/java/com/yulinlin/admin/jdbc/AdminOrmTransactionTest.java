package com.yulinlin.admin.jdbc;

import com.yulinlin.common.model.EntityCounter;
import com.yulinlin.common.model.EntityCounterManager;
import com.yulinlin.common.model.EntityCounterProperties;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.aop.JoinTransactionAop;
import com.yulinlin.data.core.exception.DuplicateDataException;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.H2Session;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IDEA 直接运行本类或单个 @Test 即可；不启动 admin，不读取 application.yml。
 * 每个测试使用独立内存 H2，结束时打印真实数据库结果并释放全部测试资源。
 * 预期的重复键/回滚异常由 assertThrows 验证，日志出现这些异常不代表测试失败。
 */
@Slf4j
@DisplayName("Admin ORM 事务人工确认（内存 H2）")
@ResourceLock("SessionUtil.route")
@ResourceLock("EntityCounterManager.current")
public class AdminOrmTransactionTest {
    private RouteSession previousRoute;
    private RouteSession route;
    private ObservedSession session;
    private Connection observer;
    private EntityCounterManager counters;

    @BeforeEach
    void prepareDatabase() throws Exception {
        previousRoute = SessionUtil.route();
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:admin_transaction_" + UUID.randomUUID());
        observer = source.getConnection(); // 独立观察连接，只读取已提交数据。
        try (var statement = observer.createStatement()) {
            statement.execute("CREATE TABLE \"admin_tx_demo\" ("
                    + "\"id\" VARCHAR(40) PRIMARY KEY, \"name\" VARCHAR(100),"
                    + "\"views\" BIGINT CHECK (\"views\" <= 5), \"version\" INTEGER)");
        }
        var proxies = EntityProxyService.newInstance();
        session = new ObservedSession(source);
        session.setGroup("test");
        session.setCoderManager(new H2CoderManager());
        session.setProxyService(proxies);
        session.setParallelConnections(3);
        session.setExecuteBatchSize(1);
        route = RouteSession.builder().proxyService(proxies).transactionLog(true).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.registerSession(session);
        route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
        new SessionUtil(route);
    }

    @AfterEach
    void showResultAndRelease(TestInfo test) throws Exception {
        try {
            while (route != null && route.isOpenTransaction()) route.rollbackTransaction();
            System.out.printf("%n场景：%s%n数据库最终数据：%s%n", test.getDisplayName(), snapshots());
        } finally {
            try { if (counters != null) counters.close(); }
            finally {
                new SessionUtil(previousRoute);
                if (session != null) session.getThreadPoolExecutor().shutdownNow();
                if (observer != null) observer.close();
            }
        }
    }

    @Test
    @DisplayName("01 正常事务：同一连接完成多次写入，外层提交后才可见")
    void normalTransactionCommitsTogether() throws Exception {
        route.transaction(() -> {
            insert("a");
            insert("b");
            assertTrue(route.isOpenTransaction());
            assertFalse(route.isRollbackOnly());
            assertEquals(List.of(), ids(), "独立观察连接不能看到尚未提交的数据");
            return null;
        });
        assertEquals(List.of("a", "b"), ids());
        assertEquals(1, session.writeConnections.size());
        assertFalse(route.isOpenTransaction());
    }

    @Test
    @DisplayName("02 外层 setRollbackOnly：不立即关闭连接，正常返回也不能提交")
    void outerRollbackOnlyPreventsCommit() throws Exception {
        var callbacks = new ArrayList<String>();
        assertThrows(OrmException.class, () -> route.transaction(() -> {
            insert("a");
            route.afterCommit(() -> callbacks.add("不能执行"));
            route.setRollbackOnly();
            assertTrue(route.isRollbackOnly());
            assertTrue(session.isOpenTransaction(), "标记回滚不会提前关闭资源");
            return null; // 没有向外抛业务异常，但提交检查仍应拒绝提交。
        }));
        assertEquals(List.of(), ids());
        assertTrue(callbacks.isEmpty());
        assertFalse(route.isOpenTransaction());
        assertFalse(session.isOpenTransaction());
        assertFalse(route.isRollbackOnly(), "事务结束后标记不能残留到下一次调用");
    }

    @Test
    @DisplayName("03 内层 setRollbackOnly：回滚当前保存点，外层仍能继续")
    void nestedRollbackOnlyDoesNotPoisonOuterTransaction() throws Exception {
        var callbacks = new ArrayList<String>();
        route.transaction(() -> {
            insert("outer-before");
            route.afterCommit(() -> callbacks.add("outer"));
            assertThrows(OrmException.class, () -> route.transaction(() -> {
                insert("inner");
                route.afterCommit(() -> callbacks.add("inner"));
                route.setRollbackOnly();
                assertTrue(route.isRollbackOnly());
                return null;
            }));
            assertFalse(route.isRollbackOnly());
            assertTrue(route.isOpenTransaction());
            insert("outer-after");
            assertTrue(callbacks.isEmpty(), "内层结束不等于物理事务提交");
            return null;
        });
        assertEquals(List.of("outer-after", "outer-before"), ids());
        assertEquals(List.of("outer"), callbacks);
        assertEquals(1, session.writeConnections.size());
    }

    @Test
    @DisplayName("04 物理事务失效：模拟 SQLState 40001，捕获异常也不能提交")
    void fatalResourceFailureCannotBeIgnored() throws Exception {
        assertThrows(OrmException.class, () -> route.transaction(() -> {
            insert("before-failure");
            // 这是注入的 JDBC 错误，验证 ORM 分类；不假装在 H2 上制造了真实死锁。
            session.nextFailure = new SQLException("测试模拟事务已回滚", "40001");
            OrmException sqlError = assertThrows(OrmException.class, () -> insert("failed"));
            assertInstanceOf(SQLException.class, sqlError.getCause());
            assertTrue(session.isRollbackOnly());
            assertTrue(route.isRollbackOnly());
            assertThrows(OrmException.class, () -> insert("must-not-run"));
            return null;
        }));
        assertEquals(List.of(), ids());
        assertFalse(session.isOpenTransaction());
    }

    @Test
    @DisplayName("05 H2 普通重复键：同一范围内捕获后，不自动标记整体回滚")
    void caughtOrdinaryH2ErrorCanContinueWithoutANestedScope() throws Exception {
        route.transaction(() -> {
            insert("a");
            assertThrows(DuplicateDataException.class, () -> insert("a"));
            assertFalse(route.isRollbackOnly());
            insert("b");
            return null;
        });
        assertEquals(List.of("a", "b"), ids());
        // 此用例说明 H2 的行为，不代表 PG 发生语句错误后也可以直接继续。
    }

    @Test
    @DisplayName("06 内层成功后外层失败：内外层数据全部回滚")
    void outerFailureAlsoRollsBackSuccessfulNestedWrites() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> route.transaction(() -> {
            insert("outer");
            route.transaction(() -> {
                insert("inner");
                var rows = ModelSelectWrapper.newInstance(DemoRow.class)
                        .autoUpdate(false)
                        .selectList();

                log.info("当前事务内的数据：{}", rows);
                return null;
            });
            throw new IllegalArgumentException("外层业务失败");
        }));
        var rows = ModelSelectWrapper.newInstance(DemoRow.class)
                .autoUpdate(false)
                .selectList();

        log.info("当前事务内的数据：{}", rows);
        assertEquals(List.of(), ids());
    }

    @Test
    @DisplayName("07 代理自动更新：恢复外层字段和版本，内层新代理失效")
    void nestedRollbackRestoresExistingProxyAndInvalidatesNewProxy() throws Exception {
        insert("user");
        DemoRow[] innerProxy = new DemoRow[1];
        route.transaction(() -> {
            DemoRow outerProxy = managed("user");
            outerProxy.setName("outer");
            assertThrows(IllegalArgumentException.class, () -> route.transaction(() -> {
                assertEquals(1, outerProxy.getVersion(), "进入内层前应刷新外层修改");
                outerProxy.setName("inner");
                route.transaction(() -> {
                    assertEquals(2, outerProxy.getVersion(), "再次嵌套使 inner 修改实际刷新为 SQL");
                    return null;
                });
                innerProxy[0] = managed("user");
                assertEquals("inner", innerProxy[0].getName());
                throw new IllegalArgumentException("回滚内层，包括已经刷新的 SQL");
            }));
            assertEquals("outer", outerProxy.getName());
            assertEquals(1, outerProxy.getVersion());
            assertThrows(NoticeException.class, () -> innerProxy[0].setName("不能再写"));
            outerProxy.setName("final"); // 外层旧代理仍可继续使用。
            return null;
        });
        Snapshot saved = snapshots().getFirst();
        assertEquals("final", saved.name());
        assertEquals(2, saved.version());
    }

    @Test
    @DisplayName("08 普通 List 插入：其中一条失败，整批回滚，保留之前已提交的数据")
    void ordinaryListFailureRollsBackTheWholeRequest() throws Exception {
        insert("existing");
        assertThrows(DuplicateDataException.class, () -> ModelInsertWrapper.newInstance(
                List.of(row("a"), row("existing"), row("b"))).execute());
        assertEquals(List.of("existing"), ids());
        assertFalse(route.isOpenTransaction());
    }

    @Test
    @DisplayName("09 事务内 batch：只用一条连接，一条失败则连同外层写入回滚")
    void transactionalBatchStaysOnOneConnection() throws Exception {
        assertThrows(DuplicateDataException.class, () -> route.transaction(() -> {
            insert("outer");
            ModelInsertWrapper.newInstance(List.of(row("a"), row("a"), row("b"))).batch().execute();
            return null;
        }));
        assertEquals(List.of(), ids());
        assertEquals(1, session.writeConnections.size());
    }

    @Test
    @DisplayName("10 事务外 batch：三个独立连接，异常返回时保留成功数据")
    void independentBatchCanPartiallySucceed() throws Exception {
        insert("existing");
        session.writeConnections.clear();
        assertThrows(DuplicateDataException.class, () -> ModelInsertWrapper.newInstance(
                List.of(row("a"), row("existing"), row("b"))).batch().execute());
        assertEquals(List.of("a", "b", "existing"), ids());
        assertEquals(3, session.writeConnections.size());
        assertFalse(route.isOpenTransaction());
    }

    @Test
    @DisplayName("11 Spring 注解兼容：同一个 ORM 切面接管，受检异常默认回滚")
    void springAnnotationUsesOrmRollbackRules() throws Exception {
        var factory = new ProxyFactory(new AnnotatedService());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new JoinTransactionAop(() -> route));
        AnnotatedService service = (AnnotatedService) factory.getProxy();
        assertThrows(java.io.IOException.class, service::saveThenFail);
        assertEquals(List.of(), ids());
        assertFalse(route.isOpenTransaction());
    }

    @Test
    @DisplayName("12 计数器 execute：内层增量随保存点回滚，外层增量保留")
    void immediateCounterJoinsTheCurrentTransaction() throws Exception {
        startCounters();
        insert("video");
        route.transaction(() -> {
            EntityCounter.update(DemoRow.class, "video").inc(DemoRow::getViews).execute();
            assertThrows(IllegalArgumentException.class, () -> route.transaction(() -> {
                EntityCounter.update(DemoRow.class, "video").inc(DemoRow::getViews, 2).execute();
                throw new IllegalArgumentException("撤销内层 +2");
            }));
            return null;
        });
        assertEquals(1L, snapshots().getFirst().views());
    }

    @Test
    @DisplayName("13 计数器 submit：失败整组回滚，修复后重试不会重复累加")
    void bufferedCounterRetriesTheRolledBackGroup() throws Exception {
        startCounters();
        insert("a");
        insert("b");
        EntityCounter.update(DemoRow.class, "a").inc(DemoRow::getViews).submit();
        EntityCounter.update(DemoRow.class, "b").inc(DemoRow::getViews, 10).submit();
        assertEquals(List.of(0L, 0L), views(), "submit 只进入内存");
        assertThrows(OrmException.class, counters::flush); // b 违反 views <= 5。
        assertEquals(List.of(0L, 0L), views(), "a 也必须回滚，不能先累加后整组重试");
        assertEquals(2, counters.pendingRows());
        EntityCounter.update(DemoRow.class, "b").dec(DemoRow::getViews, 9).submit();
        assertEquals(2, counters.flush()); // b 的待写增量：+10 -9 = +1。
        assertEquals(List.of(1L, 1L), views());
        assertEquals(0, counters.pendingRows());
    }

    @Test
    @DisplayName("14 计数器 flush：业务事务内拒绝刷新，缓冲增量不丢失")
    void counterFlushCannotJoinAnOuterBusinessTransaction() throws Exception {
        startCounters();
        insert("video");
        EntityCounter.update(DemoRow.class, "video").inc(DemoRow::getViews).submit();
        assertThrows(NoticeException.class, () -> route.transaction(() -> counters.flush()));
        assertEquals(1, counters.pendingRows());
        assertEquals(List.of(0L), views());
        counters.flush();
        assertEquals(List.of(1L), views());
    }

    private void startCounters() {
        var properties = new EntityCounterProperties();
        // 人工运行中只通过 flush 触发，避免定时任务/阈值与断点观察互相干扰。
        properties.setFlushInterval(Duration.ofDays(1));
        properties.setFlushSize(Integer.MAX_VALUE);
        counters = new EntityCounterManager(route, properties);
    }

    private static DemoRow row(String id) { return new DemoRow(id); }
    private static void insert(String id) { ModelInsertWrapper.newInstance(row(id)).execute(); }
    private static DemoRow managed(String id) {
        return ModelSelectWrapper.newInstance(DemoRow.class).autoUpdate(true).eq("id", id).selectOne();
    }
    private List<String> ids() throws SQLException { return snapshots().stream().map(Snapshot::id).toList(); }
    private List<Long> views() throws SQLException { return snapshots().stream().map(Snapshot::views).toList(); }

    /** 直接用独立 JDBC 连接核对最终结果，避免 ORM 缓存或代理影响断言。 */
    private List<Snapshot> snapshots() throws SQLException {
        var result = new ArrayList<Snapshot>();
        try (var statement = observer.createStatement();
             var rows = statement.executeQuery("SELECT \"id\", \"name\", \"views\", \"version\" FROM \"admin_tx_demo\" ORDER BY \"id\"")) {
            while (rows.next()) result.add(new Snapshot(rows.getString(1), rows.getString(2), rows.getLong(3), rows.getInt(4)));
        }
        return result;
    }

    private record Snapshot(String id, String name, long views, int version) { }

    @Data
    @JoinTable("admin_tx_demo")
    public static class DemoRow {
        @JoinMeta(primaryKey = true)
        private String id;
        private String name = "initial";
        private Long views = 0L;
        @JoinField(version = true)
        private Integer version = 0;
        public DemoRow() { }
        DemoRow(String id) { this.id = id; }
    }

    public static class AnnotatedService {
        @Transactional
        public void saveThenFail() throws java.io.IOException {
            insert("annotation");
            throw new java.io.IOException("受检异常也按 ORM 默认规则回滚");
        }
    }

    /** 只记录实际写入使用的连接；SQL 执行仍调用真实 H2 实现。 */
    private static final class ObservedSession extends H2Session {
        final Set<Connection> writeConnections = Collections.synchronizedSet(
                Collections.newSetFromMap(new IdentityHashMap<>()));
        SQLException nextFailure;
        ObservedSession(JdbcDataSource source) { super(source); }
        @Override @SneakyThrows
        protected Integer executeUpdateNode(Connection connection, List<ParseResult> results) {
            writeConnections.add(connection);
            if (nextFailure != null) {
                SQLException error = nextFailure;
                nextFailure = null;
                throw error;
            }
            return super.executeUpdateNode(connection, results);
        }
    }
}

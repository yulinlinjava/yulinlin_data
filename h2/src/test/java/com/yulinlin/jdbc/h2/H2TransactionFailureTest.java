package com.yulinlin.jdbc.h2;

import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.cache.*;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.*;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import com.yulinlin.data.core.aop.JoinTransactionAop;
import lombok.Data;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.exception.DuplicateDataException;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class H2TransactionFailureTest {
    @ParameterizedTest @ValueSource(strings = {"", ";MODE=MYSQL"})
    void caughtDuplicateCommitsOtherWritesAndRunsCallback(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            f.service.caughtDuplicate();
            assertEquals(List.of(1, 2), f.ids());
            assertEquals(1, f.callbacks);
            assertInstanceOf(SQLException.class, f.error.getCause());
            assertFalse(f.route.isOpenTransaction());
            assertFalse(f.session.isOpenTransaction());
        }
    }

    @Test void uncaughtSqlErrorRollsBackWithDefaultSpringRules() throws Exception {
        try (var f = new Fixture("")) {
            assertThrows(DuplicateDataException.class, f.service::uncaughtDuplicate);
            assertEquals(List.of(), f.ids());
            assertEquals(0, f.callbacks);
        }
    }

    @Test void innerScopeRollsBackToSavepointAndOuterContinues() throws Exception {
        try (var f = new Fixture("")) {
            f.service.caughtInnerTransaction();
            assertEquals(List.of(2), f.ids());
            assertEquals(0, f.callbacks);
        }
    }

    @Test void noRollbackForOrdinarySqlErrorUsesTheSpringRule() throws Exception {
        try (var f = new Fixture("")) {
            assertThrows(DuplicateDataException.class, f.service::noRollbackForDuplicate);
            assertEquals(List.of(1), f.ids());
            assertEquals(1, f.callbacks);
        }
    }

    @Test void caughtQueryFailureDoesNotPoisonAnOtherwiseValidTransaction() throws Exception {
        try (var f = new Fixture("")) {
            f.service.caughtQuery();
            assertEquals(List.of(1, 2), f.ids());
        }
    }

    @Test void callbackTransactionsAlsoAllowCaughtErrorsAndRollbackUncaughtOnes() throws Exception {
        try (var f = new Fixture("")) {
            f.route.transaction(() -> {
                f.insert(1);
                assertThrows(DuplicateDataException.class, () -> f.insert(1));
                assertFalse(f.route.isRollbackOnly());
                f.insert(2);
                return null;
            });
            assertEquals(List.of(1, 2), f.ids());
            assertThrows(DuplicateDataException.class, () -> f.route.transaction(() -> {
                f.insert(3);
                f.insert(1);
                return null;
            }));
            assertEquals(List.of(1, 2), f.ids());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void caughtPartialBatchStillInvalidatesCacheAfterCommit(boolean parallel) throws Exception {
        try (var f = new Fixture("")) {
            f.insert(1);
            f.cache.invalidated.clear();
            f.session.setExecuteBatchSize(1);
            f.route.transaction(() -> {
                var write = ModelInsertWrapper.newInstance(List.of(new Row(2), new Row(1), new Row(3)));
                if (parallel) write.batch();
                assertThrows(DuplicateDataException.class, write::execute);
                assertFalse(f.route.isRollbackOnly());
                assertTrue(f.cache.invalidated.isEmpty());
                TransactionUtil.afterCommit(() -> f.callbacks++);
                return null;
            });
            assertTrue(f.ids().contains(2), "successful part of the failed batch was committed");
            assertFalse(f.cache.invalidated.isEmpty(), "even a failed write can require cache invalidation");
            assertEquals(1, f.callbacks);
        }
    }

    @Test void failedStandaloneWriteDoesNotUndoEarlierCommit() throws Exception {
        try (var f = new Fixture("")) {
            f.insert(1);
            assertThrows(DuplicateDataException.class, () -> f.insert(1));
            f.insert(2);
            assertEquals(List.of(1, 2), f.ids());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void fatalFailureCannotCommitThroughCatchOrNoRollbackFor(boolean noRollbackFor) throws Exception {
        try (var f = new Fixture("")) {
            assertThrows(OrmException.class, () -> {
                if (noRollbackFor) f.service.noRollbackForFatal();
                else f.service.caughtFatal();
            });
            assertEquals(List.of(), f.ids());
            assertEquals(0, f.callbacks);
            assertFalse(f.route.isOpenTransaction());
        }
    }

    @Test void nestedSuccessStillRollsBackWithOuterAndDoesNotNotify() throws Exception {
        try (var f = new Fixture("")) {
            assertThrows(IllegalArgumentException.class, () -> f.route.transaction(() -> {
                f.insert(1);
                f.route.transaction(() -> {
                    f.insert(2);
                    f.route.afterCommit(() -> f.callbacks++);
                    return null;
                });
                assertEquals(0, f.callbacks);
                throw new IllegalArgumentException("outer");
            }));
            assertEquals(List.of(), f.ids());
            assertEquals(0, f.callbacks);
        }
    }

    @Test void lazySavepointsAndNestedCallbacksFollowTheirScope() throws Exception {
        try (var f = new Fixture("")) {
            List<Integer> callbacks = new ArrayList<>();
            f.route.transaction(() -> {
                f.route.afterCommit(() -> callbacks.add(1));
                assertThrows(DuplicateDataException.class, () -> f.route.transaction(() -> {
                    f.route.transaction(() -> {
                        f.insert(1); // 第三层首次访问数据库，需要一次建立两层保存点。
                        f.route.afterCommit(() -> callbacks.add(2));
                        return null;
                    });
                    f.insert(1);
                    return null;
                }));
                f.route.transaction(() -> {
                    f.insert(2);
                    f.route.afterCommit(() -> callbacks.add(3));
                    return null;
                });
                f.route.afterCommit(() -> callbacks.add(4));
                assertTrue(callbacks.isEmpty());
                return null;
            });
            assertEquals(List.of(2), f.ids());
            assertEquals(List.of(1, 3, 4), callbacks);
        }
    }

    @Test void rollbackOnlyBelongsToCurrentSavepoint() throws Exception {
        try (var f = new Fixture("")) {
            f.route.transaction(() -> {
                f.insert(1);
                assertThrows(OrmException.class, () -> f.route.transaction(() -> {
                    f.insert(2);
                    f.route.setRollbackOnly();
                    return null;
                }));
                assertFalse(f.route.isRollbackOnly());
                f.insert(3);
                return null;
            });
            assertEquals(List.of(1, 3), f.ids());
        }
    }

    @Test void checkedExceptionsRollBackByDefaultAndExplicitRuleCanCommit() throws Exception {
        try (var f = new Fixture("")) {
            assertThrows(java.io.IOException.class, f.service::checked);
            assertEquals(List.of(), f.ids());
            assertThrows(java.io.IOException.class, f.service::checkedNoRollback);
            assertEquals(List.of(1), f.ids());
        }
    }

    @Test void savepointRestoresProxyFieldsVersionsAndRejectsLeakedChildProxy() throws Exception {
        try (var f = new Fixture("")) {
            f.insert(1);
            Row[] leaked = new Row[1];
            f.route.transaction(() -> {
                Row parent = ModelSelectWrapper.newInstance(Row.class).autoUpdate(true).eq("id", 1).selectOne();
                parent.setValue("outer"); // 开始内层前刷新，版本应为 1。
                assertThrows(IllegalArgumentException.class, () -> f.route.transaction(() -> {
                    assertEquals(1, parent.getVersion());
                    parent.setValue("inner");
                    f.route.transaction(() -> {
                        assertEquals(2, parent.getVersion());
                        parent.setValue("deep");
                        return null;
                    });
                    assertEquals(3, parent.getVersion());
                    leaked[0] = ModelSelectWrapper.newInstance(Row.class).autoUpdate(true).eq("id", 1).selectOne();
                    throw new IllegalArgumentException("rollback child");
                }));
                assertEquals("outer", parent.getValue());
                assertEquals(1, parent.getVersion());
                assertThrows(RuntimeException.class, () -> leaked[0].setValue("must not persist"));
                parent.setValue("after");
                return null;
            });
            Row row = ModelSelectWrapper.newInstance(Row.class).autoUpdate(false).eq("id", 1).selectOne();
            assertEquals("after", row.getValue());
            assertEquals(2, row.getVersion());
        }
    }

    @Test void rolledBackChildDoesNotInvalidateCacheOrRunItsCallbacks() throws Exception {
        try (var f = new Fixture("")) {
            f.cache.invalidated.clear();
            f.route.transaction(() -> {
                assertThrows(IllegalStateException.class, () -> f.route.transaction(() -> {
                    f.insert(1);
                    f.route.afterCommit(() -> f.callbacks++);
                    throw new IllegalStateException("child");
                }));
                return null;
            });
            assertTrue(f.ids().isEmpty());
            assertTrue(f.cache.invalidated.isEmpty());
            assertEquals(0, f.callbacks);
        }
    }

    @Test void independentParallelBatchRetainsSuccessAndInvalidatesCacheOnFailure() throws Exception {
        try (var f = new Fixture("")) {
            f.insert(1);
            f.cache.invalidated.clear();
            f.session.setParallelConnections(3);
            f.session.setExecuteBatchSize(1);
            f.session.writeConnections.clear();
            assertThrows(DuplicateDataException.class, () -> ModelInsertWrapper.newInstance(
                    List.of(new Row(2), new Row(1), new Row(3))).batch().execute());
            assertEquals(List.of(1, 2, 3), f.ids());
            assertEquals(3, f.session.writeConnections.size());
            assertFalse(f.cache.invalidated.isEmpty());
            assertFalse(f.route.isOpenTransaction());
        }
    }

    @Test void batchInsideExplicitTransactionUsesOneConnectionAndRollsBackAll() throws Exception {
        try (var f = new Fixture("")) {
            f.session.setParallelConnections(4);
            f.route.transaction(() -> {
                f.insert(1);
                assertFalse(f.session.supportsParallelWrites());
                assertThrows(DuplicateDataException.class, () -> f.route.transaction(() -> {
                    ModelInsertWrapper.newInstance(List.of(new Row(2), new Row(1), new Row(3))).batch().execute();
                    return null;
                }));
                f.insert(4);
                return null;
            });
            assertEquals(List.of(1, 4), f.ids());
            assertEquals(1, f.session.writeConnections.size());
        }
    }

    @Test void afterCommitFailureDoesNotUndoCommittedDataOrLeakContext() throws Exception {
        try (var f = new Fixture("")) {
            assertThrows(IllegalStateException.class, () -> f.route.transaction(() -> {
                f.insert(1);
                f.route.afterCommit(() -> { throw new IllegalStateException("notification"); });
                return null;
            }));
            assertFalse(f.route.isOpenTransaction());
            f.insert(2);
            assertEquals(List.of(1, 2), f.ids());
        }
    }

    @Test void transactionRejectsAnotherDatasourceBeforeItsWrite() throws Exception {
        try (var f = new Fixture(""); var other = new Fixture("")) {
            other.session.setGroup("other");
            f.route.registerSession(other.session);
            new SessionUtil(f.route);
            f.route.transaction(() -> {
                ModelInsertWrapper.newInstance("h2", new Row(1)).execute();
                assertThrows(OrmException.class, () -> ModelInsertWrapper.newInstance("other", new Row(2)).execute());
                ModelInsertWrapper.newInstance("h2", new Row(3)).execute();
                return null;
            });
            assertEquals(List.of(1, 3), f.ids());
            assertTrue(other.ids().isEmpty());
            assertFalse(other.session.isOpenTransaction());
        }
    }

    @Test void backendWithoutTransactionsKeepsIndependentWritesButRejectsExplicitScope() throws Exception {
        try (var f = new Fixture("")) {
            f.session.transactionsSupported = false;
            f.insert(1);
            assertThrows(OrmException.class, () -> f.route.transaction(() -> {
                f.insert(2);
                return null;
            }));
            assertEquals(List.of(1), f.ids());
            assertFalse(f.session.isOpenTransaction());
        }
    }

    @Test void tenantFilterCanChooseTheFinalDatasourceBeforeTransactionBinding() throws Exception {
        var filter = new com.yulinlin.data.core.filter.IFilterManager() {
            @Override public com.yulinlin.data.core.request.BaseRequest before(String group, com.yulinlin.data.core.request.BaseRequest request) {
                request.setSession("other");
                return request;
            }
            @Override public Object after(String group, com.yulinlin.data.core.request.BaseRequest request, Object data) { return data; }
        };
        try (var f = new Fixture("", filter); var other = new Fixture("")) {
            other.session.setGroup("other");
            f.route.registerSession(other.session);
            new SessionUtil(f.route);
            f.route.transaction(() -> {
                ModelInsertWrapper.newInstance("h2", new Row(1)).execute();
                ModelInsertWrapper.newInstance("h2", new Row(2)).execute();
                return null;
            });
            assertTrue(f.ids().isEmpty());
            assertEquals(List.of(1, 2), other.ids());
            assertFalse(f.session.isOpenTransaction());
        }
    }

    public static class Service {
        final Fixture f;
        Service inner;
        Service(Fixture f) { this.f = f; }
        @Transactional public void checked() throws java.io.IOException {
            f.insert(1);
            throw new java.io.IOException("checked");
        }
        @Transactional(noRollbackFor = java.io.IOException.class)
        public void checkedNoRollback() throws java.io.IOException { checked(); }
        @Transactional public void caughtDuplicate() {
            f.insert(1);
            try { f.insert(1); } catch (DuplicateDataException error) { f.error = error; }
            assertFalse(f.route.isRollbackOnly());
            f.insert(2);
            TransactionUtil.afterCommit(() -> f.callbacks++);
        }
        @Transactional public void uncaughtDuplicate() {
            f.insert(1);
            TransactionUtil.afterCommit(() -> f.callbacks++);
            f.insert(1);
        }
        @Transactional public void caughtInnerTransaction() {
            try { inner.uncaughtDuplicate(); } catch (DuplicateDataException error) { f.error = error; }
            assertFalse(f.route.isRollbackOnly());
            f.insert(2);
        }
        @Transactional(noRollbackFor = DuplicateDataException.class)
        public void noRollbackForDuplicate() { uncaughtDuplicate(); }
        @Transactional public void caughtQuery() {
            f.insert(1);
            assertThrows(OrmException.class, () -> ModelSelectWrapper.newInstance(Row.class)
                    .eq("missing_column", 1).count());
            assertFalse(f.route.isRollbackOnly());
            f.insert(2);
        }
        @Transactional public void caughtFatal() {
            try { noRollbackForFatal(); } catch (OrmException expected) { }
            assertTrue(f.route.isRollbackOnly());
            assertThrows(OrmException.class, () -> f.insert(3));
        }
        @Transactional(noRollbackFor = OrmException.class)
        public void noRollbackForFatal() {
            f.insert(1);
            TransactionUtil.afterCommit(() -> f.callbacks++);
            f.session.nextFailure = new SQLException("simulated transaction rollback", "40001");
            f.insert(2);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final RouteSession previous = SessionUtil.route();
        final TestSession session;
        final RouteSession route;
        final Connection anchor;
        final Service service;
        final RecordingCache cache = new RecordingCache();
        int callbacks;
        DuplicateDataException error;
        Fixture(String mode) throws Exception { this(mode, com.yulinlin.data.core.filter.IFilterManager.NONE); }
        Fixture(String mode, com.yulinlin.data.core.filter.IFilterManager filters) throws Exception {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:transaction_failure_" + UUID.randomUUID() + mode);
            anchor = source.getConnection();
            try (var s = anchor.createStatement()) { s.execute("CREATE TABLE \"tx_rows\" (\"id\" INTEGER PRIMARY KEY, \"value\" VARCHAR(100), \"version\" INTEGER DEFAULT 0)"); }
            session = new TestSession(source);
            session.setGroup("h2");
            session.setCoderManager(new H2CoderManager());
            session.setProxyService(EntityProxyService.newInstance());
            route = RouteSession.builder().proxyService(EntityProxyService.newInstance()).filterManager(filters).build();
            route.setLoadBalance(new RandomLoadBalance());
            route.registerSession(session);
            route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                    DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
            route.setQueryCache(cache);
            route.setClearOnWrite(true);
            new SessionUtil(route);
            var target = new Service(this);
            var proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            proxy.addAdvisor(new JoinTransactionAop(() -> route));
            service = (Service) proxy.getProxy();
            target.inner = service;
        }
        void insert(int id) { ModelInsertWrapper.newInstance(new Row(id)).execute(); }
        List<Integer> ids() throws SQLException {
            var ids = new ArrayList<Integer>();
            try (var s = anchor.createStatement(); var rows = s.executeQuery("SELECT \"id\" FROM \"tx_rows\" ORDER BY \"id\"")) {
                while (rows.next()) ids.add(rows.getInt(1));
            }
            return ids;
        }
        @Override public void close() throws Exception {
            try { if (route.isOpenTransaction()) route.rollbackTransaction(); }
            finally {
                new SessionUtil(previous);
                session.getThreadPoolExecutor().shutdownNow();
                anchor.close();
            }
        }
    }

    private static final class TestSession extends H2Session {
        SQLException nextFailure;
        boolean transactionsSupported = true;
        final Set<Connection> writeConnections = Collections.synchronizedSet(
                Collections.newSetFromMap(new IdentityHashMap<>()));
        TestSession(JdbcDataSource source) { super(source); }
        @Override public boolean supportsTransactions() { return transactionsSupported; }
        @Override @lombok.SneakyThrows
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

    private static final class RecordingCache implements QueryCache {
        final Set<CacheNamespace> invalidated = new HashSet<>();
        @Override public CacheLookup get(CacheKey key, CacheValueType type) { return CacheLookup.miss(); }
        @Override public void put(CacheKey key, CacheValueType type, Object value, Duration ttl) { }
        @Override public void invalidate(Set<CacheNamespace> namespaces) { invalidated.addAll(namespaces); }
    }

    @Data @JoinTable("tx_rows")
    public static class Row {
        @JoinMeta(primaryKey = true)
        private Integer id;
        private String value;
        @JoinField(version = true)
        private Integer version = 0;
        public Row() { }
        Row(int id) { this.id = id; }
    }
}

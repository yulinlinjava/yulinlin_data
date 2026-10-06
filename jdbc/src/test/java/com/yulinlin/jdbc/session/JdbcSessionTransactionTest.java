package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.jdbc.JdbcSessionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Deterministic connection/lifecycle tests; does not contact a MySQL server. */
class JdbcSessionTransactionTest {
    private final List<FakeJdbcSession> sessions = new ArrayList<>();

    @AfterEach void closeExecutors() {
        sessions.forEach(session -> session.getThreadPoolExecutor().shutdownNow());
    }

    @Test void commonDefaultsAndValidation() {
        assertThat(new JdbcSessionProperties().getParallelConnections()).isEqualTo(4);
        assertThat(session(new Database()).getParallelConnections()).isEqualTo(4);
        assertThatThrownBy(() -> new JdbcSessionProperties().setParallelConnections(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JdbcSessionProperties().setExecuteBatchSize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void standaloneSessionAutoCommitsAndReleasesWithoutRouter() throws Exception {
        Database db = new Database();
        FakeJdbcSession session = session(db, "sync");
        assertThat(session.run()).isEqualTo(1);
        assertThat(session.isOpenTransaction()).isFalse();
        verify(db.connections.getFirst()).setAutoCommit(false);
        verify(db.connections.getFirst()).commit();
        verify(db.connections.getFirst()).close();
    }

    @Test void twoSessionsHaveIndependentTransactionsEvenOnSameDataSource() throws Exception {
        Database db = new Database();
        FakeJdbcSession first = session(db, "sync");
        FakeJdbcSession second = session(db, "sync");
        first.startTransaction(); first.run();
        assertThat(second.isOpenTransaction()).isFalse();
        second.startTransaction(); second.run();
        first.commitTransaction();
        assertThat(second.isOpenTransaction()).isTrue();
        second.rollbackTransaction();
        verify(db.connections.get(0)).commit();
        verify(db.connections.get(0), never()).rollback();
        verify(db.connections.get(1), never()).commit();
        verify(db.connections.get(1)).rollback();
        db.connections.forEach(connection -> { try { verify(connection).close(); } catch (SQLException e) { throw new AssertionError(e); } });
    }

    @Test void defaultParallelBatchesUseAtMostFourConnectionsAndWaitBeforeCommit() throws Exception {
        checkParallelLimit(4);
    }

    @Test void configuredParallelLimitIsRespected() throws Exception { checkParallelLimit(2); }

    @Test void default128RowThresholdActuallyEnablesBatchWorkers() {
        Database db = new Database(); FakeJdbcSession session = session(db);
        session.groups = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> java.util.Collections.nCopies(128, new ParseResult(null, "async", null))).toList();
        ExecuteRequest<Object> request = ExecuteRequest.ofInsert(Object.class);
        request.setBatch(true); // Keep the real default threshold, rather than the test helper's 1.
        assertThat(request.getBatchSize()).isEqualTo(128);
        assertThat(session.insert(request)).isEqualTo(512);
        assertThat(session.workerThreads).doesNotContain(Thread.currentThread().getName());
        assertThat(db.connections.size()).isBetween(1, 4);
    }

    private void checkParallelLimit(int limit) throws Exception {
        Database db = new Database();
        FakeJdbcSession session = session(db, "slow", "slow", "slow", "slow", "slow", "slow", "slow", "slow");
        session.setParallelConnections(limit);
        session.started = new CountDownLatch(limit);
        session.release = new CountDownLatch(1);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<Integer> result = caller.submit(session::run);
            try {
                assertThat(session.started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                for (Connection connection : db.connections) { verify(connection, never()).commit(); verify(connection, never()).close(); }
            } finally { session.release.countDown(); }
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(8);
        }
        assertThat(session.peak.get()).isEqualTo(limit);
        assertThat(db.connections).hasSize(limit);
        for (Connection connection : db.connections) { verify(connection).commit(); verify(connection).close(); }
    }

    @Test void mixedSyncAndAsyncCountsAllResultsAndDrainsWorkers() throws Exception {
        Database db = new Database();
        FakeJdbcSession session = session(db, "slow", "sync");
        session.started = new CountDownLatch(1); session.release = new CountDownLatch(1);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<Integer> result = caller.submit(session::run);
            try {
                assertThat(session.started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
            } finally { session.release.countDown(); }
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(2);
        }
        for (Connection connection : db.connections) { verify(connection).commit(); verify(connection).close(); }
    }

    @Test void firstAsyncFailureStillWaitsForOtherWorkersBeforeRollback() throws Exception {
        Database db = new Database();
        FakeJdbcSession session = session(db, "fail", "slow", "sync");
        session.started = new CountDownLatch(1); session.release = new CountDownLatch(1);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<Integer> result = caller.submit(session::run);
            try {
                assertThat(session.started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                for (Connection connection : db.connections) { verify(connection, never()).rollback(); verify(connection, never()).close(); }
            } finally { session.release.countDown(); }
            assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class);
        }
        assertThat(session.active.get()).isZero();
        for (Connection connection : db.connections) { verify(connection, never()).commit(); verify(connection).rollback(); verify(connection).close(); }
    }

    @Test void synchronousFailureAfterSubmissionAlsoDrainsWorkers() throws Exception {
        Database db = new Database();
        FakeJdbcSession session = session(db, "slow", "sync-fail");
        session.started = new CountDownLatch(1); session.release = new CountDownLatch(1);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<Integer> result = caller.submit(session::run);
            try {
                assertThat(session.started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
            } finally { session.release.countDown(); }
            assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class);
        }
        for (Connection connection : db.connections) { verify(connection, never()).commit(); verify(connection).rollback(); verify(connection).close(); }
    }

    @Test void rejectedSubmissionRollsBackAndClosesSeedConnection() throws Exception {
        Database db = new Database(); FakeJdbcSession session = session(db, "async");
        session.getThreadPoolExecutor().shutdownNow();
        assertThatThrownBy(session::run).isInstanceOf(RejectedExecutionException.class);
        verify(db.connections.getFirst()).rollback(); verify(db.connections.getFirst()).close();
        assertThat(session.isOpenTransaction()).isFalse();
    }

    @Test void caughtInnerRollbackPreventsOuterCommit() throws Exception {
        Database db = new Database(); FakeJdbcSession session = session(db, "sync");
        session.startTransaction(); session.run(); session.startTransaction();
        session.rollbackTransaction();
        assertThat(session.isOpenTransaction()).isTrue();
        assertThatThrownBy(session::commitTransaction).isInstanceOf(IllegalStateException.class).hasMessageContaining("rollback-only");
        verify(db.connections.getFirst(), never()).commit(); verify(db.connections.getFirst()).rollback(); verify(db.connections.getFirst()).close();
    }

    @Test void springBoundConnectionStaysOnOwnerThreadAndSpringCommitsIt() throws Exception {
        Database db = new Database(); FakeJdbcSession session = session(db, "async", "async", "async");
        String owner = Thread.currentThread().getName();
        new TransactionTemplate(new DataSourceTransactionManager(db.source)).execute(status -> {
            assertThat(session.run()).isEqualTo(3);
            assertThat(session.workerThreads).containsOnly(owner);
            try { verify(db.connections.getFirst(), never()).commit(); verify(db.connections.getFirst(), never()).close(); }
            catch (SQLException e) { throw new AssertionError(e); }
            return null;
        });
        assertThat(db.connections).hasSize(1);
        verify(db.connections.getFirst()).commit(); verify(db.connections.getFirst()).close();
    }

    @Test void caughtFailureMarksSpringTransactionRollbackOnly() throws Exception {
        Database db = new Database(); FakeJdbcSession session = session(db, "fail");
        assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(db.source)).execute(status -> {
            assertThatThrownBy(session::run).isInstanceOf(IllegalArgumentException.class);
            return null;
        })).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
        verify(db.connections.getFirst(), never()).commit(); verify(db.connections.getFirst()).rollback(); verify(db.connections.getFirst()).close();
    }

    @Test void realSpringAopOrderDoesNotDoubleCloseOrCommitCaughtFailure() throws Exception {
        var previousRoute = SessionUtil.route();
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext(AopConfiguration.class)) {
            Database db = context.getBean(Database.class);
            FakeJdbcSession session = context.getBean(FakeJdbcSession.class);
            AopService service = context.getBean(AopService.class);
            session.groups = List.of(List.of(new ParseResult(null, "async", null)));
            service.save();
            verify(db.connections.getFirst()).commit(); verify(db.connections.getFirst()).close();
            session.groups = List.of(List.of(new ParseResult(null, "fail", null)));
            assertThatThrownBy(service::catchFailure).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
            verify(db.connections.get(1), never()).commit(); verify(db.connections.get(1)).rollback(); verify(db.connections.get(1)).close();
            assertThat(context.getBean(RouteSession.class).isOpenTransaction()).isFalse();
            assertThat(session.isOpenTransaction()).isFalse();
        } finally { new SessionUtil(previousRoute); }
    }

    @Test void hikariCapacityLimitsTransactionParallelism() throws Exception {
        var source = mock(com.zaxxer.hikari.HikariDataSource.class);
        when(source.getMaximumPoolSize()).thenReturn(1);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        FakeJdbcSession session = new FakeJdbcSession(source); sessions.add(session);
        session.groups = List.of(List.of(new ParseResult(null, "async", null)), List.of(new ParseResult(null, "async", null)));
        assertThat(session.run()).isEqualTo(2);
        assertThat(session.workerThreads).containsOnly(Thread.currentThread().getName());
        verify(source).getConnection(); verify(connection).commit(); verify(connection).close();
    }

    @Test void waitingForDataSourceConnectionDoesNotBlockOtherBorrowersFromReleasing() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection first = mock(Connection.class); Connection second = mock(Connection.class);
        when(first.getAutoCommit()).thenReturn(true); when(second.getAutoCommit()).thenReturn(true);
        CountDownLatch acquiring = new CountDownLatch(1); CountDownLatch allowAcquire = new CountDownLatch(1);
        AtomicInteger acquisitions = new AtomicInteger();
        when(source.getConnection()).thenAnswer(invocation -> {
            if (acquisitions.incrementAndGet() == 1) return first;
            acquiring.countDown();
            if (!allowAcquire.await(10, TimeUnit.SECONDS)) throw new SQLException("acquisition timed out");
            return second;
        });
        ConnectionPool pool = new ConnectionPool(source, 2).initialize();
        assertThat(pool.getConnection()).isSameAs(first);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<?> acquiringConnection = workers.submit(() -> {
                Connection connection = pool.getConnection(); pool.releaseConnection(connection);
            });
            try {
                assertThat(acquiring.await(10, TimeUnit.SECONDS)).isTrue();
                workers.submit(() -> pool.releaseConnection(first)).get(2, TimeUnit.SECONDS);
            } finally { allowAcquire.countDown(); }
            acquiringConnection.get(10, TimeUnit.SECONDS);
        } finally { pool.finish(true); }
        verify(first).rollback(); verify(first).close();
        verify(second).rollback(); verify(second).close();
    }

    @Test void connectionCompletionFailureDoesNotPreventRemainingCleanup() throws Exception {
        Database db = new Database();
        ConnectionPool pool = new ConnectionPool(db.source, 2).initialize();
        Connection first = pool.getConnection(); Connection second = pool.getConnection();
        pool.releaseConnection(first); pool.releaseConnection(second);
        doThrow(new SQLException("first commit")).when(first).commit();
        doThrow(new SQLException("second rollback")).when(second).rollback();
        doThrow(new SQLException("first close")).when(first).close();
        assertThatThrownBy(() -> pool.finish(false)).isInstanceOf(SQLException.class).hasMessage("first commit")
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(2));
        verify(first).rollback(); verify(first).close(); verify(second, never()).commit(); verify(second).rollback(); verify(second).close();
        assertThat(pool.getConnections()).isEmpty();
    }

    @Test void caughtNestedListenerFailureDoesNotPrematurelyCloseOuterRoute() throws Exception {
        Database db = new Database(); FakeJdbcSession first = session(db, "sync");
        // A commit listener can perform ORM writes; exceptions must unwind only its own scope.
        var listener = mock(com.yulinlin.data.core.transaction.TransactionListener.class);
        var manager = new TransactionListenerManager(List.of(listener));
        RouteSession failing = RouteSession.builder().transactionListenerManager(manager).build();
        LoadBalance balance = mock(LoadBalance.class);
        when(balance.loadBalance("first", JoinCluster.master)).thenReturn(first);
        failing.setLoadBalance(balance);
        doThrow(new IllegalArgumentException("listener")).when(listener).commitTransaction();
        assertThatThrownBy(() -> failing.transaction(() -> {
            ((FakeJdbcSession) failing.session("first")).run();
            assertThatThrownBy(() -> failing.transaction(() -> null)).isInstanceOf(IllegalArgumentException.class);
            assertThat(failing.isOpenTransaction()).isTrue();
            verify(db.connections.getFirst(), never()).close();
            return null;
        })).isInstanceOf(IllegalStateException.class);
        verify(db.connections.getFirst(), never()).commit(); verify(db.connections.getFirst()).rollback(); verify(db.connections.getFirst()).close();
    }

    @Test void routerJoinsOnlyItsParticipantsAndPreservesStandaloneOuterScope() throws Exception {
        Database db = new Database(); FakeJdbcSession first = session(db, "sync"); FakeJdbcSession second = session(db, "sync");
        RouteSession route = route(first, second);
        first.startTransaction(); first.run();
        route.transaction(() -> { ((FakeJdbcSession) route.session("first")).run(); return null; });
        assertThat(first.isOpenTransaction()).isTrue();
        verify(db.connections.getFirst(), never()).commit();
        first.rollbackTransaction();
        assertThat(second.isOpenTransaction()).isFalse();
        assertThat(db.connections).hasSize(1);
    }

    @Test void routeCommitFailureRollsBackRemainingSessionsAndReleasesAll() throws Exception {
        Database firstDb = new Database(); Database secondDb = new Database();
        FakeJdbcSession first = session(firstDb, "sync"); FakeJdbcSession second = session(secondDb, "sync");
        RouteSession route = route(first, second);
        assertThatThrownBy(() -> route.transaction(() -> {
            ((FakeJdbcSession) route.session("first")).run();
            ((FakeJdbcSession) route.session("second")).run();
            doThrow(new SQLException("commit failed")).when(firstDb.connections.getFirst()).commit();
            return null;
        })).isInstanceOf(SQLException.class).hasMessage("commit failed");
        verify(firstDb.connections.getFirst()).rollback(); verify(firstDb.connections.getFirst()).close();
        verify(secondDb.connections.getFirst(), never()).commit(); verify(secondDb.connections.getFirst()).rollback(); verify(secondDb.connections.getFirst()).close();
        assertThat(route.isOpenTransaction()).isFalse(); assertThat(first.isOpenTransaction()).isFalse(); assertThat(second.isOpenTransaction()).isFalse();
    }

    @Test void caughtNestedRouteFailureRollsBackAllParticipants() throws Exception {
        Database firstDb = new Database(); Database secondDb = new Database();
        RouteSession route = route(session(firstDb, "sync"), session(secondDb, "sync"));
        assertThatThrownBy(() -> route.transaction(() -> {
            ((FakeJdbcSession) route.session("first")).run();
            assertThatThrownBy(() -> route.transaction(() -> {
                ((FakeJdbcSession) route.session("second")).run();
                throw new IllegalArgumentException("inner");
            })).isInstanceOf(IllegalArgumentException.class);
            return null;
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("rollback-only");
        for (Database db : List.of(firstDb, secondDb)) {
            verify(db.connections.getFirst(), never()).commit(); verify(db.connections.getFirst()).rollback(); verify(db.connections.getFirst()).close();
        }
    }

    private RouteSession route(FakeJdbcSession first, FakeJdbcSession second) {
        RouteSession route = RouteSession.builder().transactionListenerManager(new TransactionListenerManager()).build();
        LoadBalance balance = mock(LoadBalance.class);
        when(balance.loadBalance("first", JoinCluster.master)).thenReturn(first);
        when(balance.loadBalance("second", JoinCluster.master)).thenReturn(second);
        route.setLoadBalance(balance);
        return route;
    }

    private FakeJdbcSession session(Database db, String... markers) {
        FakeJdbcSession session = new FakeJdbcSession(db.source);
        session.groups = java.util.Arrays.stream(markers).map(marker -> List.of(new ParseResult(null, marker, null))).toList();
        sessions.add(session);
        return session;
    }

    private static final class Database {
        final DataSource source = mock(DataSource.class);
        final List<Connection> connections = new CopyOnWriteArrayList<>();
        Database() {
            try {
                when(source.getConnection()).thenAnswer(invocation -> {
                    Connection connection = mock(Connection.class);
                    when(connection.getAutoCommit()).thenReturn(true);
                    connections.add(connection);
                    return connection;
                });
            } catch (SQLException e) { throw new AssertionError(e); }
        }
    }

    private static class FakeJdbcSession extends JdbcSession {
        List<List<ParseResult>> groups = List.of();
        CountDownLatch started;
        CountDownLatch release;
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final List<String> workerThreads = new CopyOnWriteArrayList<>();
        FakeJdbcSession(DataSource source) {
            super(source);
            setThreadPoolExecutor((ThreadPoolExecutor) Executors.newFixedThreadPool(8));
        }
        int run() {
            ExecuteRequest<Object> request = ExecuteRequest.ofInsert(Object.class);
            request.setBatch(true); request.setBatchSize(1);
            return insert(request);
        }
        @Override protected <T extends com.yulinlin.data.core.cache.CacheKey> List<ParseResult> parseNodes(RequestType type, Object root, List<INode> nodes, Class clazz) {
            return groups.stream().flatMap(List::stream).toList();
        }
        @Override protected boolean isOpenAsync(ExecuteRequest request, List<ParseResult> results) {
            return !results.getFirst().getRequest().toString().startsWith("sync") && super.isOpenAsync(request, results);
        }
        @Override protected Integer executeUpdateNode(Connection connection, List<ParseResult> results) {
            String marker = results.getFirst().getRequest().toString();
            workerThreads.add(Thread.currentThread().getName());
            int count = active.incrementAndGet(); peak.accumulateAndGet(count, Math::max);
            try {
                if (marker.contains("fail")) throw new IllegalArgumentException("batch failed");
                if (marker.equals("slow") && release != null) {
                    started.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("workers timed out"); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                }
                return results.size();
            } finally { active.decrementAndGet(); }
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class AopConfiguration {
        @org.springframework.context.annotation.Bean Database database() { return new Database(); }
        @org.springframework.context.annotation.Bean DataSource dataSource(Database db) { return db.source; }
        @org.springframework.context.annotation.Bean(destroyMethod = "shutdownWorkers") AopJdbcSession testSession(Database db) { return new AopJdbcSession(db.source); }
        @org.springframework.context.annotation.Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @org.springframework.context.annotation.Bean RouteSession routeSession(FakeJdbcSession session) {
            var route = RouteSession.builder().transactionListenerManager(new TransactionListenerManager()).build();
            LoadBalance balance = mock(LoadBalance.class);
            when(balance.loadBalance("first", JoinCluster.master)).thenReturn(session);
            route.setLoadBalance(balance); return route;
        }
        @org.springframework.context.annotation.Bean SessionUtil sessionUtil(RouteSession route) { return new SessionUtil(route); }
        @org.springframework.context.annotation.Bean com.yulinlin.jdbc.aop.SpringTransactionAop springTransactionAop(SessionUtil ignored) {
            return new com.yulinlin.jdbc.aop.SpringTransactionAop();
        }
        @org.springframework.context.annotation.Bean AopService aopService(RouteSession route) { return new AopService(() -> ((FakeJdbcSession) route.session("first")).run()); }
    }

    static class AopJdbcSession extends FakeJdbcSession {
        AopJdbcSession(DataSource source) { super(source); }
        public void shutdownWorkers() { getThreadPoolExecutor().shutdownNow(); }
    }

    public static class AopService {
        private final Runnable operation;
        public AopService(Runnable operation) { this.operation = operation; }
        @org.springframework.transaction.annotation.Transactional public void save() { operation.run(); }
        @org.springframework.transaction.annotation.Transactional public void catchFailure() {
            try { operation.run(); } catch (IllegalArgumentException ignored) { }
        }
    }
}

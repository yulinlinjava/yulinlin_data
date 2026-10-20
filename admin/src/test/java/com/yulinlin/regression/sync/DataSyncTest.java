package com.yulinlin.regression.sync;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.filter.IFilter;
import com.yulinlin.data.core.filter.SimpFilterManager;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.sync.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅测试源码：不启动业务应用、不连接数据库、不声明自动建表实体。 */
@Timeout(10)
class DataSyncTest {
    static class Video {
        String id;
        List<String> tags;
        long views;
    }

    @AfterEach void cleanup() {
        if (TransactionListenerManager.currentContext() != null)
            TransactionListenerManager.currentContext().manager().rollbackTransaction();
    }

    @Test void patternsRespectPackageBoundaries() {
        assertTrue(new EntityPattern(List.of("java.lang.String")).matches(String.class));
        assertFalse(new EntityPattern(List.of("java.util.*")).matches(AtomicInteger.class));
        assertTrue(new EntityPattern(List.of("java.util.**")).matches(AtomicInteger.class));
        assertTrue(new EntityPattern(List.of("java.util.**")).matches(ArrayList.class));
        assertTrue(new EntityPattern(List.of("java.*.concurrent.**")).matches(AtomicInteger.class));
        assertTrue(new EntityPattern(List.of("java.lang.Str*")).matches(String.class));
        assertTrue(new EntityPattern(List.of("**.String")).matches(String.class));
        assertFalse(new EntityPattern(List.of("**")).matches(Object.class));
        assertThrows(IllegalArgumentException.class, () -> new EntityPattern(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EntityPattern(List.of("com..entity")));
        assertThrows(IllegalArgumentException.class, () -> new EntityPattern(List.of("com.**Entity")));
    }

    @Test void snapshotsDetachConditionsAndMutableValuesAndCanBeReplayedAgain() {
        ArrayList<String> tags = new ArrayList<>(List.of("a"));
        UpdateWrapper<Video> wrapper = new UpdateWrapper<>();
        wrapper.table("videos").fields(fields -> fields.field("tags", tags))
                .where(where -> where.eq("id", "1"));
        ExecuteRequest<?> request = ExecuteRequest.ofUpdate(Video.class).addRequest(wrapper);
        request.setRoot(new Video());
        request.queue();
        SyncTask task = new SyncTask(rule(), request);
        tags.add("b");
        wrapper.table("changed");
        ExecuteRequest<?> snapshot = task.newRequest();
        assertNull(snapshot.getRoot());
        assertFalse(snapshot.isQueued());
        UpdateWrapper<?> copied = (UpdateWrapper<?>) snapshot.getWrappers().getFirst();
        assertEquals("videos", copied.getFrom().getName());
        assertEquals(List.of("a"), copied.fields().getList().iterator().next().getValue());
        copied.table("mutated-target-request");
        assertEquals("videos", ((UpdateWrapper<?>) task.newRequest().getWrappers().getFirst()).getFrom().getName());
    }

    @Test void enqueueOnlyAfterOutermostCommitAndNeverAfterRollback() throws Exception {
        List<SyncTask> written = new CopyOnWriteArrayList<>();
        MemorySyncTaskStore store = new MemorySyncTaskStore();
        try (SyncManager sync = new SyncManager(properties(10), store, written::add)) {
            sync.start(Set.of("mysql", "postgresql"));
            TransactionListenerManager transaction = new TransactionListenerManager();
            transaction.startTransaction();
            transaction.startTransaction();
            sync.capture("mysql", update());
            transaction.commitTransaction();
            assertEquals(0, store.pendingTasks(rule().name()));
            assertTrue(written.isEmpty());
            transaction.commitTransaction();
            await(() -> sync.statuses().getFirst().completed() == 1);
            transaction.startTransaction();
            sync.capture("mysql", update());
            transaction.rollbackTransaction();
            assertEquals(1, written.size());
        }
    }

    @Test void failureKeepsHeadAndOverflowReportsGapWithoutRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (SyncManager sync = new SyncManager(properties(1), new MemorySyncTaskStore(), task -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("模拟目标库失败");
        })) {
            sync.start(Set.of("mysql", "postgresql"));
            commit(sync, update());
            await(() -> sync.statuses().getFirst().state().equals("PAUSED"));
            assertEquals(1, sync.statuses().getFirst().pendingTasks());
            assertNotNull(sync.statuses().getFirst().failedTaskId());
            commit(sync, update());
            assertEquals(1, attempts.get());
            assertEquals(1, sync.statuses().getFirst().rejected());
            assertTrue(sync.statuses().getFirst().requiresRepair());
            sync.resumeAfterRepair(rule().name(), true);
            assertEquals(0, sync.statuses().getFirst().pendingTasks());
        }
    }

    @Test void memoryCapacityCountsNodesAndAcknowledgesOnlyHead() {
        MemorySyncTaskStore store = new MemorySyncTaskStore();
        SyncTask task = new SyncTask(rule(), update());
        assertTrue(store.offer(rule().name(), task, 1));
        assertFalse(store.offer(rule().name(), new SyncTask(rule(), update()), 1));
        assertSame(task, store.peek(rule().name()));
        assertThrows(IllegalStateException.class, () -> store.acknowledge(rule().name(), "wrong-id"));
        store.acknowledge(rule().name(), task.id());
        assertEquals(0, store.pendingNodes(rule().name()));
    }

    @Test void sourceCommitRegisteredBeforeFullSyncStillMarksConflict() {
        MemorySyncTaskStore store = new MemorySyncTaskStore();
        try (SyncManager sync = new SyncManager(properties(10), store, task -> fail("全量期间不能消费"))) {
            sync.start(Set.of("mysql", "postgresql"));
            TransactionListenerManager transaction = new TransactionListenerManager();
            transaction.startTransaction();
            sync.capture("mysql", update());
            try (SyncManager.ManualGuard guard = sync.beginManual("postgresql")) {
                transaction.commitTransaction();
                assertTrue(guard.conflicted());
                assertThrows(IllegalStateException.class, guard::check);
                assertThrows(IllegalStateException.class, () -> sync.resumeAfterRepair(rule().name(), false));
                assertEquals(1, store.pendingTasks(rule().name()));
            }
            assertEquals("PAUSED", sync.statuses().getFirst().state());
        }
    }

    @Test void startupValidatesGroupsAndOnlyStartsIdleIncrementalRules() {
        DataSyncProperties properties = properties(10);
        try (SyncManager sync = new SyncManager(properties, new MemorySyncTaskStore(), task -> fail("空队列不应写入"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> sync.start(Set.of("mysql")));
            sync.start(Set.of("mysql", "postgresql"));
            assertEquals("RUNNING", sync.statuses().getFirst().state());
            assertEquals(0, sync.statuses().getFirst().pendingTasks());
            assertEquals(0, sync.statuses().getFirst().completed());
        }
    }

    @Test void startupAllowsManualOnlyModeWithoutRules() {
        try (SyncManager sync = new SyncManager(new DataSyncProperties(), new MemorySyncTaskStore(),
                task -> fail("未配置规则不应写入"))) {
            sync.start(Set.of("mysql", "postgresql"));
            assertTrue(sync.statuses().isEmpty());
        }
    }

    @Test void rawSqlAndUnknownClassesAreNotForwarded() {
        MemorySyncTaskStore store = new MemorySyncTaskStore();
        try (SyncManager sync = new SyncManager(properties(10), store, task -> fail("不应执行"))) {
            sync.start(Set.of("mysql", "postgresql"));
            ExecuteRequest<?> sql = ExecuteRequest.ofUpdate(Video.class)
                    .addRequest(new CommandNode<>("delete from videos", ParseType.update));
            commit(sync, sql);
            ExecuteRequest<?> unknown = update();
            unknown.setFromClass(Object.class);
            commit(sync, unknown);
            assertEquals(0, store.pendingTasks(rule().name()));
        }
    }

    @Test void matchingExpressionsDoNotDuplicateTasksAndRulesAreIsolated() throws Exception {
        DataSyncProperties properties = properties(10);
        DataSyncProperties.Rule extra = new DataSyncProperties.Rule();
        extra.setName("second");
        extra.setSource("mysql");
        extra.setTarget("h2");
        extra.setEntities(List.of(Video.class.getName()));
        properties.setRules(List.of(properties.getRules().getFirst(), extra));
        List<String> written = new CopyOnWriteArrayList<>();
        try (SyncManager sync = new SyncManager(properties, new MemorySyncTaskStore(), task -> {
            if (task.rule().target().equals("postgresql")) throw new IllegalStateException("失败目标");
            written.add(task.rule().target());
        })) {
            sync.start(Set.of("mysql", "postgresql", "h2"));
            commit(sync, update());
            await(() -> sync.statuses().get(0).state().equals("PAUSED")
                    && sync.statuses().get(1).completed() == 1);
            assertEquals(1, sync.statuses().get(0).pendingTasks());
            assertEquals(List.of("h2"), written);
        }
    }

    @Test void targetReplaySkipsBusinessFiltersButCommitsTargetTransaction() {
        IFilter filter = mock(IFilter.class);
        EntitySession target = mock(EntitySession.class);
        when(target.group()).thenReturn("postgresql");
        when(target.cluster()).thenReturn(JoinCluster.master);
        when(target.weight()).thenReturn(1);
        when(target.update(any())).thenReturn(1);
        RouteSession route = RouteSession.builder()
                .filterManager(new SimpFilterManager(List.of(filter))).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.registerSession(target);
        assertEquals(1, route.replayWrite("postgresql", update()));
        verify(target).startTransaction();
        verify(target).commitTransaction();
        verify(target, never()).rollbackTransaction();
        verify(filter, never()).before(anyString(), any());
        verify(filter, never()).after(anyString(), any(), any());
    }

    private static SyncRule rule() {
        return new SyncRule(null, "mysql", "postgresql", List.of(Video.class.getName()));
    }
    private static DataSyncProperties properties(int capacity) {
        DataSyncProperties properties = new DataSyncProperties();
        properties.setEnabled(true);
        properties.setCapacity(capacity);
        DataSyncProperties.Rule config = new DataSyncProperties.Rule();
        config.setSource("mysql");
        config.setTarget("postgresql");
        config.setEntities(List.of(Video.class.getName(), "com.yulinlin.regression.sync.**"));
        properties.setRules(List.of(config));
        return properties;
    }
    private static ExecuteRequest<?> update() {
        UpdateWrapper<Video> wrapper = new UpdateWrapper<>();
        wrapper.table("videos").fields(fields -> fields.field("views", 10L))
                .where(where -> where.eq("id", "1"));
        return ExecuteRequest.ofUpdate(Video.class).addRequest(wrapper);
    }
    private static void commit(SyncManager sync, ExecuteRequest<?> request) {
        TransactionListenerManager transaction = new TransactionListenerManager();
        transaction.startTransaction();
        sync.capture("mysql", request);
        transaction.commitTransaction();
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
}

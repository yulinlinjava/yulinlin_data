package com.yulinlin.common.model;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * EntityCounter 的进程内聚合器。更新和插入共用刷新调度，分别通过标准 Wrapper/RouteSession 执行。
 */
@Slf4j
public final class EntityCounterManager implements AutoCloseable {

    record Command(String session, Class<?> entityClass, Object id, String primaryKey,
                   Map<String, BigDecimal> deltas, Boolean clearCache) {
    }

    private record RowKey(String session, Class<?> entityClass, Object id, String primaryKey) {
    }

    private record GroupKey(String session, Class<?> entityClass) {
    }

    private record InsertRow(CounterInsert.Snapshot snapshot, boolean clearCache) {
        InsertRow merge(InsertRow other) {
            return new InsertRow(snapshot.merge(other.snapshot), clearCache || other.clearCache);
        }
    }

    /** 已经构造的请求保留主键和自动填充值，失败重试不重新生成 ID，也不并入下一窗口。 */
    private static final class InsertBatch {
        final GroupKey key;
        final List<InsertRow> rows;
        ExecuteRequest<?> request;
        InsertBatch(GroupKey key, List<InsertRow> rows) { this.key = key; this.rows = rows; }
    }

    private record Window(ConcurrentHashMap<RowKey, RowAccumulator> updates,
                          ConcurrentHashMap<CounterInsert.Key, InsertRow> inserts) { }

    private record RowSnapshot(RowKey key, Map<String, BigDecimal> deltas, boolean clearCache) {
    }

    private static final class RowAccumulator {
        private Map<String, BigDecimal> deltas = Map.of();
        private boolean clearCache;

        synchronized void add(Map<String, BigDecimal> values, boolean clear) {
            // 一行的多个字段原子合并，计算失败时不留下部分增量。
            Map<String, BigDecimal> merged = new LinkedHashMap<>(deltas);
            values.forEach((field, delta) -> merged.merge(field, delta, BigDecimal::add));
            deltas = merged;
            clearCache |= clear;
        }

        synchronized RowSnapshot snapshot(RowKey key) {
            TreeMap<String, BigDecimal> sorted = new TreeMap<>();
            deltas.forEach((field, delta) -> {
                if (delta.signum() != 0) sorted.put(field, delta);
            });
            return new RowSnapshot(key,
                    Collections.unmodifiableMap(new LinkedHashMap<>(sorted)), clearCache);
        }
    }

    private static final AtomicReference<EntityCounterManager> CURRENT = new AtomicReference<>();

    private final RouteSession routeSession;
    private final EntityCounterProperties properties;
    private final ReentrantReadWriteLock activeLock = new ReentrantReadWriteLock();
    private volatile ConcurrentHashMap<RowKey, RowAccumulator> active = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<CounterInsert.Key, InsertRow> activeInserts = new ConcurrentHashMap<>();
    // 仅 flush 持锁操作；对外计数使用 volatile 快照，不在提交锁内反向获取 flush 锁。
    private List<InsertBatch> retryInserts = new ArrayList<>();
    private volatile int retryInsertRows;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean immediateFlushRunning = new AtomicBoolean();
    private final AtomicBoolean immediateFlushRequested = new AtomicBoolean();
    private final ScheduledFuture<?> schedule;

    public EntityCounterManager(RouteSession routeSession, EntityCounterProperties properties) {
        this.routeSession = Objects.requireNonNull(routeSession, "routeSession");
        this.properties = Objects.requireNonNull(properties, "properties");
        CURRENT.set(this);
        this.schedule = ThreadUtil.scheduleVirtual(this::flushSafely, properties.getFlushInterval().toMillis());
    }

    static EntityCounterManager required() {
        EntityCounterManager manager = CURRENT.get();
        if (manager == null) {
            throw new IllegalStateException("EntityCounterManager is not configured; enable common auto-configuration");
        }
        return manager;
    }

    int executeUpdate(Command command) {
        ensureOpen();
        ExecuteRequest<?> request = request(command, clearCache(command));
        if (request == null) return 0;
        return update(request);
    }

    int executeInsert(String session, Object entity, Boolean clearCache) {
        ensureOpen();
        CounterInsert.Snapshot snapshot = CounterInsert.snapshot(
                routeSession.resolveWriteGroup(entity.getClass(), session), entity);
        return insert(new InsertBatch(new GroupKey(snapshot.key().session(), entity.getClass()),
                List.of(new InsertRow(snapshot, clearCache == null ? properties.isClearCache() : clearCache))));
    }

    void submitInsert(String session, Object entity, Boolean clearCache) {
        ensureOpen();
        if (routeSession.isOpenTransaction())
            throw new NoticeException("EntityCounter.insert().submit() 不能加入当前事务，请改用 execute() 或在事务外提交。");
        CounterInsert.Snapshot snapshot = CounterInsert.snapshot(
                routeSession.resolveWriteGroup(entity.getClass(), session), entity);
        InsertRow row = new InsertRow(snapshot, clearCache == null ? properties.isClearCache() : clearCache);
        boolean flushNow;
        activeLock.readLock().lock();
        try {
            ensureOpen();
            activeInserts.merge(snapshot.key(), row, InsertRow::merge);
            flushNow = pendingRows() >= properties.getFlushSize();
        } finally {
            activeLock.readLock().unlock();
        }
        if (flushNow) requestImmediateFlush();
    }

    void submit(Command command) {
        ensureOpen();
        if (command.deltas().isEmpty()) return;
        if (routeSession.isOpenTransaction()) {
            throw new NoticeException("EntityCounter.submit() 不能加入当前事务，请改用 execute() 或在事务外提交。");
        }
        String group = routeSession.resolveWriteGroup(command.entityClass(), command.session());

        boolean flushNow;
        activeLock.readLock().lock();
        try {
            ensureOpen();
            RowKey key = new RowKey(group, command.entityClass(), command.id(), command.primaryKey());
            active.computeIfAbsent(key, ignored -> new RowAccumulator())
                    .add(command.deltas(), clearCache(command));
            flushNow = pendingRows() >= properties.getFlushSize();
        } finally {
            activeLock.readLock().unlock();
        }
        if (flushNow) requestImmediateFlush();
    }

    /** 当前等待刷新的不同行数，不是 submit 调用次数。 */
    public int pendingRows() {
        return active.size() + activeInserts.size() + retryInsertRows;
    }

    /** 手动刷新一个稳定快照；刷新期间的新提交留到下一轮。 */
    public synchronized int flush() {
        Window window = swapActive();
        ConcurrentHashMap<RowKey, RowAccumulator> snapshot = window.updates();
        if (snapshot.isEmpty() && window.inserts().isEmpty() && retryInserts.isEmpty()) return 0;

        LinkedHashMap<GroupKey, List<RowSnapshot>> groups = new LinkedHashMap<>();
        snapshot.forEach((key, accumulator) -> {
            RowSnapshot row = accumulator.snapshot(key);
            if (!row.deltas().isEmpty()) {
                groups.computeIfAbsent(new GroupKey(key.session(), key.entityClass()), ignored -> new ArrayList<>())
                        .add(row);
            }
        });

        int affected = 0;
        Throwable failure = null;
        for (List<RowSnapshot> rows : groups.values()) {
            try {
                affected += executeGroup(rows);
            } catch (Throwable error) {
                restore(rows);
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        LinkedHashMap<GroupKey, List<InsertRow>> insertGroups = new LinkedHashMap<>();
        window.inserts().values().forEach(row -> {
            CounterInsert.Key key = row.snapshot().key();
            insertGroups.computeIfAbsent(new GroupKey(key.session(), key.entityClass()), ignored -> new ArrayList<>())
                    .add(row);
        });
        List<InsertBatch> batches = new ArrayList<>(retryInserts);
        insertGroups.forEach((key, rows) -> batches.add(new InsertBatch(key, rows)));
        List<InsertBatch> failed = new ArrayList<>();
        for (InsertBatch batch : batches) {
            try {
                affected += insert(batch);
            } catch (Throwable error) {
                failed.add(batch);
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        retryInserts = failed;
        retryInsertRows = failed.stream().mapToInt(batch -> batch.rows.size()).sum();
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        if (failure != null) throw new IllegalStateException(failure);
        return affected;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int insert(InsertBatch batch) {
        if (batch.request == null) {
            ExecuteRequest<?> merged = null;
            for (InsertRow row : batch.rows) {
                // 到真正刷新时才触发 insertBefore，自动 ID/时间不参与入队时的维度。
                ModelInsertWrapper<?> wrapper = ModelInsertWrapper.newInstance(
                        batch.key.session(), row.snapshot().entity());
                wrapper.clearCache(row.clearCache());
                if (merged == null) merged = wrapper.getRequest();
                else merged.mergeQueued(wrapper.getRequest());
            }
            // 仍使用 JDBC executeBatch，但一个聚合插入组不拆多连接，缩小部分提交的风险。
            merged.setBatch(false);
            batch.request = merged;
        }
        return routeSession.insert((ExecuteRequest) batch.request);
    }

    private int executeGroup(List<RowSnapshot> rows) {
        ExecuteRequest<?> merged = null;
        for (RowSnapshot row : rows) {
            Command command = new Command(row.key().session(), row.key().entityClass(), row.key().id(),
                    row.key().primaryKey(), row.deltas(), row.clearCache());
            ExecuteRequest<?> request = request(command, row.clearCache());
            if (request == null) continue;
            if (merged == null) merged = request;
            else merged.mergeQueued(request);
        }
        if (merged == null) return 0;
        merged.setBatch(rows.size() > 1);
        return update(merged);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ExecuteRequest<?> request(Command command, boolean clearCache) {
        if (command.deltas().isEmpty()) return null;
        JoinTable table = AnnotationUtil.findAnnotation(command.entityClass(), JoinTable.class);
        if (table == null) throw new IllegalArgumentException("计数更新实体缺少 @JoinTable：" + command.entityClass().getName());
        // 不走实体更新工厂，避免默认字段和 updateBefore 被转换成覆盖赋值。
        IUpdateWrapper wrapper = routeSession.getWrapperFactory().createUpdateWrapper();
        wrapper.table(table.value());
        command.deltas().forEach((field, delta) -> {
            if (delta.signum() > 0) wrapper.fields().inc(field, CounterNumbers.parameter(delta));
            else if (delta.signum() < 0) wrapper.fields().dec(field, CounterNumbers.parameter(delta.negate()));
        });
        ((IConditionWrapper) wrapper.where()).eq(command.primaryKey(), command.id());
        ExecuteRequest<?> request = ExecuteRequest.ofUpdate(command.entityClass());
        request.setSession(command.session());
        request.addRequest(wrapper);
        return request.clearCache(clearCache);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int update(ExecuteRequest<?> request) {
        return routeSession.update((ExecuteRequest) request);
    }

    private boolean clearCache(Command command) {
        return command.clearCache() == null ? properties.isClearCache() : command.clearCache();
    }

    private Window swapActive() {
        activeLock.writeLock().lock();
        try {
            Window snapshot = new Window(active, activeInserts);
            active = new ConcurrentHashMap<>();
            activeInserts = new ConcurrentHashMap<>();
            return snapshot;
        } finally {
            activeLock.writeLock().unlock();
        }
    }

    private void restore(List<RowSnapshot> rows) {
        activeLock.readLock().lock();
        try {
            for (RowSnapshot row : rows) {
                active.computeIfAbsent(row.key(), ignored -> new RowAccumulator())
                        .add(row.deltas(), row.clearCache());
            }
        } finally {
            activeLock.readLock().unlock();
        }
    }

    private void requestImmediateFlush() {
        if (closed.get()) return;
        immediateFlushRequested.set(true);
        if (!immediateFlushRunning.compareAndSet(false, true)) return;
        ThreadUtil.submitVirtual(() -> {
            try {
                while (!closed.get() && immediateFlushRequested.getAndSet(false)) flushSafely();
            } finally {
                immediateFlushRunning.set(false);
                if (!closed.get() && immediateFlushRequested.get()) requestImmediateFlush();
            }
        });
    }

    private void flushSafely() {
        try {
            flush();
        } catch (Throwable error) {
            log.error("实体计数器刷新失败，更新增量已放回活动区，失败插入批次保留待重试", error);
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("EntityCounterManager is closed");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        schedule.cancel(false);
        try {
            flush();
        } catch (Throwable error) {
            log.error("应用关闭时实体计数器仍有数据未能写入，pendingRows={}", pendingRows(), error);
        } finally {
            CURRENT.compareAndSet(this, null);
        }
    }
}

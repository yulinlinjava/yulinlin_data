package com.yulinlin.common.model;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.lang.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
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
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * EntityCounter 的进程内聚合器。刷新后仍通过标准 ModelUpdateWrapper/RouteSession 路径执行。
 */
@Slf4j
public final class EntityCounterManager implements AutoCloseable {

    record Command(String session, Class<?> entityClass, Object id, String primaryKey,
                   Map<String, Long> deltas, Boolean clearCache) {
    }

    private record RowKey(String session, Class<?> entityClass, Object id, String primaryKey) {
    }

    private record GroupKey(String session, Class<?> entityClass) {
    }

    private record RowSnapshot(RowKey key, Map<String, Long> deltas, boolean clearCache) {
    }

    private static final class RowAccumulator {
        private final ConcurrentHashMap<String, LongAdder> deltas = new ConcurrentHashMap<>();
        private final AtomicBoolean clearCache = new AtomicBoolean();

        void add(Map<String, Long> values, boolean clear) {
            values.forEach((field, delta) -> deltas.computeIfAbsent(field, ignored -> new LongAdder()).add(delta));
            if (clear) clearCache.set(true);
        }

        RowSnapshot snapshot(RowKey key) {
            TreeMap<String, Long> sorted = new TreeMap<>();
            deltas.forEach((field, delta) -> {
                long value = delta.sum();
                if (value != 0) sorted.put(field, value);
            });
            return new RowSnapshot(key,
                    Collections.unmodifiableMap(new LinkedHashMap<>(sorted)), clearCache.get());
        }
    }

    private static final AtomicReference<EntityCounterManager> CURRENT = new AtomicReference<>();

    private final RouteSession routeSession;
    private final EntityCounterProperties properties;
    private final ReentrantReadWriteLock activeLock = new ReentrantReadWriteLock();
    private volatile ConcurrentHashMap<RowKey, RowAccumulator> active = new ConcurrentHashMap<>();
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

    int execute(Command command) {
        ensureOpen();
        ExecuteRequest<?> request = request(command, clearCache(command));
        if (request == null) return 0;
        return update(request);
    }

    void submit(Command command) {
        ensureOpen();
        if (command.deltas().isEmpty()) return;
        if (routeSession.isOpenTransaction()
                || TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new NoticeException("EntityCounter.submit() 不能加入当前事务，请改用 execute() 或在事务外提交。");
        }

        boolean flushNow;
        activeLock.readLock().lock();
        try {
            ensureOpen();
            RowKey key = new RowKey(command.session(), command.entityClass(), command.id(), command.primaryKey());
            active.computeIfAbsent(key, ignored -> new RowAccumulator())
                    .add(command.deltas(), clearCache(command));
            flushNow = active.size() >= properties.getFlushSize();
        } finally {
            activeLock.readLock().unlock();
        }
        if (flushNow) requestImmediateFlush();
    }

    /** 当前等待刷新的不同行数，不是 submit 调用次数。 */
    public int pendingRows() {
        return active.size();
    }

    /** 手动刷新一个稳定快照；刷新期间的新提交留到下一轮。 */
    public synchronized int flush() {
        ConcurrentHashMap<RowKey, RowAccumulator> snapshot = swapActive();
        if (snapshot.isEmpty()) return 0;

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
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        if (failure != null) throw new IllegalStateException(failure);
        return affected;
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
        ModelUpdateWrapper wrapper = command.session() == null
                ? ModelUpdateWrapper.newInstance(command.entityClass())
                : ModelUpdateWrapper.newInstance(command.session(), command.entityClass());
        command.deltas().forEach((field, delta) -> {
            if (delta > 0) wrapper.inc(field, delta);
            else if (delta < 0) wrapper.dec(field, -delta);
        });
        wrapper.eq(command.primaryKey(), command.id());
        wrapper.clearCache(clearCache);
        return wrapper.getRequest();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int update(ExecuteRequest<?> request) {
        return routeSession.update((ExecuteRequest) request);
    }

    private boolean clearCache(Command command) {
        return command.clearCache() == null ? properties.isClearCache() : command.clearCache();
    }

    private ConcurrentHashMap<RowKey, RowAccumulator> swapActive() {
        activeLock.writeLock().lock();
        try {
            ConcurrentHashMap<RowKey, RowAccumulator> snapshot = active;
            active = new ConcurrentHashMap<>();
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
            log.error("实体计数器刷新失败，增量已放回待刷新区", error);
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

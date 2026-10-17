package com.yulinlin.data.lang.lazy;

import com.yulinlin.data.lang.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A small stale-while-revalidate cache.
 * <p>
 * The first read loads synchronously. After the TTL expires, readers keep receiving the stale value while
 * exactly one virtual thread refreshes it. A successful refresh starts a new TTL; a failed or {@code null}
 * refresh keeps the previous value and uses a short retry window.
 */
public final class SmartCache<T> implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SmartCache.class);
    private static final long MAX_RETRY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final ConcurrentMap<Object, SmartCache<?>> cacheMap = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock cacheMapLock = new ReentrantReadWriteLock();

    private final Supplier<T> supplier;
    private final long ttlNanos;
    private final long retryNanos;
    private final Object registryKey;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean backgroundRefreshing = new AtomicBoolean();
    private final ScheduledFuture<?> autoRefreshTask;

    private volatile CacheState<T> state = CacheState.empty(0);
    private volatile boolean closed;

    private SmartCache(Object registryKey, Supplier<T> supplier, int ttlSeconds, boolean autoRefresh) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
        this.registryKey = registryKey;
        this.supplier = Objects.requireNonNull(supplier, "supplier");
        this.ttlNanos = TimeUnit.SECONDS.toNanos(ttlSeconds);
        this.retryNanos = Math.min(ttlNanos, MAX_RETRY_NANOS);
        this.autoRefreshTask = autoRefresh
                ? ThreadUtil.scheduleVirtual(this::autoRefresh, TimeUnit.SECONDS.toMillis(ttlSeconds))
                : null;
    }

    /** Reloads the value synchronously, regardless of its current expiry time. */
    public void refresh() {
        checkOpen();
        lock.lock();
        try {
            checkOpen();
            refreshValue(state);
        } finally {
            lock.unlock();
        }
    }

    /** Reloads the value once on a virtual thread. Repeated calls while refreshing are coalesced. */
    public void refreshAsync() {
        checkOpen();
        scheduleBackgroundRefresh(state.version());
    }

    /** Returns whether no value has been loaded or the current value has reached its refresh deadline. */
    public boolean isExpired() {
        CacheState<T> current = state;
        return !current.initialized() || isDue(current, System.nanoTime());
    }

    /**
     * Returns the cached value. The first load is synchronous; an expired value is refreshed in the background.
     */
    public T get() {
        checkOpen();
        CacheState<T> current = state;
        if (!current.initialized()) {
            current = loadFirstValue();
        } else if (isDue(current, System.nanoTime())) {
            scheduleBackgroundRefresh(current.version());
        }
        return current.value();
    }

    /** Clears this instance. The next {@link #get()} performs a synchronous load. */
    public void clear() {
        checkOpen();
        lock.lock();
        try {
            checkOpen();
            state = CacheState.empty(state.version() + 1);
        } finally {
            lock.unlock();
        }
    }

    /** Cancels automatic refresh, releases the cached value, and unregisters a keyed cache. */
    @Override
    public void close() {
        if (registryKey == null) {
            closeState();
            return;
        }

        boolean needsClear;
        cacheMapLock.writeLock().lock();
        try {
            cacheMap.remove(registryKey, this);
            needsClear = beginClose();
        } finally {
            cacheMapLock.writeLock().unlock();
        }
        if (needsClear) clearClosedState();
    }

    private CacheState<T> loadFirstValue() {
        lock.lock();
        try {
            checkOpen();
            CacheState<T> current = state;
            return current.initialized() ? current : refreshValue(current);
        } finally {
            lock.unlock();
        }
    }

    private CacheState<T> refreshValue(CacheState<T> before) {
        try {
            T loaded = supplier.get();
            if (closed) return state;

            long lifetime = loaded == null ? retryNanos : ttlNanos;
            T nextValue = loaded == null ? before.value() : loaded;
            CacheState<T> next = new CacheState<>(true, nextValue,
                    deadline(System.nanoTime(), lifetime), before.version() + 1);
            state = next;
            return next;
        } catch (RuntimeException | Error error) {
            if (!closed && before.initialized()) {
                state = new CacheState<>(true, before.value(),
                        deadline(System.nanoTime(), retryNanos), before.version() + 1);
            }
            throw error;
        }
    }

    private void scheduleBackgroundRefresh(long expectedVersion) {
        if (closed || !backgroundRefreshing.compareAndSet(false, true)) return;
        try {
            ThreadUtil.submitVirtual(() -> runBackgroundRefresh(expectedVersion));
        } catch (RuntimeException | Error error) {
            backgroundRefreshing.set(false);
            log.warn("SmartCache 后台刷新任务提交失败，继续使用旧缓存值", error);
        }
    }

    private void autoRefresh() {
        CacheState<T> current = state;
        if (current.initialized() && !isDue(current, System.nanoTime())) return;
        if (closed || !backgroundRefreshing.compareAndSet(false, true)) return;
        runBackgroundRefresh(current.version());
    }

    private void runBackgroundRefresh(long expectedVersion) {
        try {
            lock.lock();
            try {
                if (closed || state.version() != expectedVersion) return;
                refreshValue(state);
            } finally {
                lock.unlock();
            }
        } catch (RuntimeException | Error error) {
            log.warn("SmartCache 异步刷新失败，已保留旧缓存值", error);
        } finally {
            backgroundRefreshing.set(false);
        }
    }

    private void closeState() {
        if (beginClose()) clearClosedState();
    }

    private synchronized boolean beginClose() {
        if (closed) return false;
        closed = true;
        if (autoRefreshTask != null) autoRefreshTask.cancel(false);
        return true;
    }

    private void clearClosedState() {
        lock.lock();
        try {
            state = CacheState.empty(state.version() + 1);
        } finally {
            lock.unlock();
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("SmartCache is closed");
    }

    private static boolean isDue(CacheState<?> state, long now) {
        return now - state.expireTimeNanos() >= 0;
    }

    private static long deadline(long now, long durationNanos) {
        return now + durationNanos;
    }

    public static <T> SmartCache<T> of(Supplier<T> supplier, int ttlSeconds, boolean autoRefresh) {
        return new SmartCache<>(null, supplier, ttlSeconds, autoRefresh);
    }

    public static <T> SmartCache<T> of(Supplier<T> supplier, int ttlSeconds) {
        return of(supplier, ttlSeconds, false);
    }

    public static <T> SmartCache<T> of(Supplier<T> supplier) {
        return of(supplier, 30, false);
    }

    /**
     * Returns the process-wide cache registered under {@code key}. The first creation defines its supplier,
     * value type, TTL, and automatic-refresh setting until that instance is closed.
     */
    @SuppressWarnings("unchecked")
    public static <T> SmartCache<T> of(Object key, Supplier<T> supplier, int ttlSeconds, boolean autoRefresh) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        cacheMapLock.readLock().lock();
        try {
            return (SmartCache<T>) cacheMap.computeIfAbsent(key,
                    currentKey -> new SmartCache<>(currentKey, supplier, ttlSeconds, autoRefresh));
        } finally {
            cacheMapLock.readLock().unlock();
        }
    }

    public static <T> SmartCache<T> of(Object key, Supplier<T> supplier, int ttlSeconds) {
        return of(key, supplier, ttlSeconds, false);
    }

    public static <T> SmartCache<T> of(Object key, Supplier<T> supplier) {
        return of(key, supplier, 30, false);
    }

    /** Atomically unregisters and closes every keyed cache. */
    public static void clearAllCache() {
        ArrayList<SmartCache<?>> caches;
        cacheMapLock.writeLock().lock();
        try {
            caches = new ArrayList<>(cacheMap.values());
            cacheMap.clear();
            caches.removeIf(cache -> !cache.beginClose());
        } finally {
            cacheMapLock.writeLock().unlock();
        }
        caches.forEach(SmartCache::clearClosedState);
    }

    private record CacheState<T>(boolean initialized, T value, long expireTimeNanos, long version) {
        private static <T> CacheState<T> empty(long version) {
            return new CacheState<>(false, null, 0, version);
        }
    }
}

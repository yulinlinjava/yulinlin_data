package com.yulinlin.data.lang.lazy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class SmartCache<T> implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SmartCache.class);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2), runnable -> {
                Thread thread = new Thread(runnable, "smart-cache-worker");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<Object, SmartCache<?>> cacheMap = new ConcurrentHashMap<>();

    private final Supplier<T> supplier;
    private final int ttlSeconds;
    private final ScheduledFuture<?> autoRefreshTask;

    private volatile T value;
    private volatile long expireTimeNanos;
    private volatile boolean closed;
    private final ReentrantLock lock = new ReentrantLock();

    private SmartCache(Supplier<T> supplier, int ttlSeconds, boolean autoRefresh) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
        this.supplier = java.util.Objects.requireNonNull(supplier, "supplier");
        this.ttlSeconds = ttlSeconds;
        this.autoRefreshTask = autoRefresh
                ? scheduler.scheduleAtFixedRate(this::safeRefresh, ttlSeconds, ttlSeconds, TimeUnit.SECONDS)
                : null;
    }

    private void safeRefresh() {
        if (closed) return;
        if (!lock.tryLock()) return;
        try {
            if (!closed) refreshValue();
        } catch (Exception e) {
            log.warn("SmartCache auto-refresh error", e);
        } finally {
            lock.unlock();
        }
    }

    // 刷新缓存
    public void refresh() {
        checkOpen();
        lock.lock();
        try {
            checkOpen();
            refreshValue();
        } finally {
            lock.unlock();
        }
    }

    private void refreshValue() {
        T newValue = supplier.get();
        if (newValue != null) {
            this.value = newValue;
            this.expireTimeNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(ttlSeconds);
        }
    }

    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);


    public boolean isExpired() {
        return System.nanoTime() >= expireTimeNanos;
    }

    public void refreshAsync() {
        checkOpen();
        if (isRefreshing.compareAndSet(false, true)) {
            scheduler.submit(() -> {
                try {
                    if (!closed) refresh();
                } finally {
                    isRefreshing.set(false);
                }
            });
        }
    }

    // 获取缓存数据
    public T get() {
        checkOpen();

        if (value == null) {
            // 首次加载，仍然阻塞等待加载
            lock.lock();
            try {
                if (value == null) {
                    checkOpen();
                    refreshValue(); // 同步加载
                }
            } finally {
                lock.unlock();
            }
        } else if (isExpired()) {
            // 已过期，异步刷新

            refreshAsync();


        }
        return value;
    }

    // 清除当前缓存
    public void clear() {
        value = null;
        expireTimeNanos = 0;
    }

    /** Cancels automatic refresh and releases the cached value. */
    @Override
    public void close() {
        closed = true;
        if (autoRefreshTask != null) autoRefreshTask.cancel(false);
        lock.lock();
        try {
            clear();
        } finally {
            lock.unlock();
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("SmartCache is closed");
    }



    public static <T> SmartCache<T> of(Supplier<T> supplier, int ttlSeconds, boolean autoRefresh) {
        return new SmartCache<>(supplier, ttlSeconds, autoRefresh);
    }
    public static <T> SmartCache<T> of(Supplier<T> supplier, int ttlSeconds) {
        return new SmartCache<>(supplier, ttlSeconds, false);
    }
    public static <T> SmartCache<T> of(Supplier<T> supplier) {
        return new SmartCache<>(supplier, 30, false);
    }
    // --------- 工厂方法（带缓存） ---------
    @SuppressWarnings("unchecked")
    public static <T> SmartCache<T> of(Object key, Supplier<T> supplier, int ttlSeconds, boolean autoRefresh) {
        return (SmartCache<T>) cacheMap.computeIfAbsent(key, k -> new SmartCache<>(supplier, ttlSeconds, autoRefresh));
    }

    public static <T> SmartCache<T> of(Object key, Supplier<T> supplier, int ttlSeconds) {
        return of(key,supplier,ttlSeconds,false);
    }
    public static <T> SmartCache<T> of(Object key, Supplier<T> supplier) {
        return of(key,supplier,30,false);
    }
    // 清除所有缓存
    public static void clearAllCache() {
        cacheMap.values().forEach(SmartCache::close);
        cacheMap.clear();
    }


}

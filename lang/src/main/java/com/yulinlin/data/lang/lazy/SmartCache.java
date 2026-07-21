package com.yulinlin.data.lang.lazy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class SmartCache<T> {
    private static final Logger log = LoggerFactory.getLogger(SmartCache.class);
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    private static final Map<Object, SmartCache<?>> cacheMap = new ConcurrentHashMap<>();

    private final Supplier<T> supplier;
    private final int ttlSeconds;
    private final boolean autoRefresh;

    private volatile T value;
    private volatile long expireTime = 0L;
    private final ReentrantLock lock = new ReentrantLock();

    private SmartCache(Supplier<T> supplier, int ttlSeconds, boolean autoRefresh) {
        this.supplier = supplier;
        this.ttlSeconds = ttlSeconds;
        this.autoRefresh = autoRefresh;

        if (autoRefresh) {
            scheduler.scheduleAtFixedRate(this::safeRefresh, ttlSeconds, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    private void safeRefresh() {
        if (lock.tryLock()) {
            try {
                refresh();
            } catch (Exception e) {
                log.warn("SmartCache auto-refresh error", e);
            } finally {
                lock.unlock();
            }
        }
    }

    // 刷新缓存
    public void refresh() {
        T newValue = supplier.get();
        if (newValue != null) {
            this.value = newValue;
            this.expireTime = System.currentTimeMillis() + ttlSeconds * 1000L;
        }
    }

    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);


    public boolean isExpired() {
        return System.currentTimeMillis() >= expireTime;
    }

    public void refreshAsync() {
        if (isRefreshing.compareAndSet(false, true)) {
            scheduler.submit(() -> {
                try {
                    refresh();
                } finally {
                    isRefreshing.set(false);
                }
            });
        }
    }

    // 获取缓存数据
    public T get() {

        if (value == null) {
            // 首次加载，仍然阻塞等待加载
            lock.lock();
            try {
                if (value == null) {
                    refresh(); // 同步加载
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
        expireTime = 0;
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
        cacheMap.values().forEach(SmartCache::clear);
        cacheMap.clear();
    }


}

package com.yulinlin.data.lang.cache;

import com.yulinlin.data.lang.util.ThreadUtil;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** Small dependency-free expiring map. Query caching lives in separate cache modules. */
public class ExpiryMap<K, V> {

    private static final int DEFAULT_MAX_SIZE = 20_000;
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "expiry-map-cleaner");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<K, Entry<V>> values = new ConcurrentHashMap<>();
    private final Queue<V> expiredQueue = new ConcurrentLinkedQueue<>();
    private final Consumer<Queue<V>> consumer;
    private final long durationNanos;
    private final long maximumSize;
    private final int randomTtlSeconds;
    private final ScheduledFuture<?> cleanupTask;

    public ExpiryMap() {
        this(1, TimeUnit.MINUTES, DEFAULT_MAX_SIZE, null);
    }

    public ExpiryMap(Consumer<Queue<V>> onRemoval) {
        this(1, TimeUnit.MINUTES, DEFAULT_MAX_SIZE, onRemoval);
    }

    public ExpiryMap(long duration, TimeUnit unit, long maximumSize, Consumer<Queue<V>> consumer) {
        this(duration, unit, maximumSize, 0, consumer);
    }

    public ExpiryMap(long duration, TimeUnit unit, long maximumSize, int randomTtl,
                     Consumer<Queue<V>> consumer) {
        if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
        if (maximumSize <= 0) throw new IllegalArgumentException("maximumSize must be positive");
        this.durationNanos = unit.toNanos(duration);
        this.maximumSize = maximumSize;
        this.randomTtlSeconds = Math.max(0, randomTtl);
        this.consumer = consumer;
        this.cleanupTask = CLEANER.scheduleAtFixedRate(this::cleanUp, 1, 1, TimeUnit.MINUTES);
    }

    public void put(K key, V value) {
        if (values.size() >= maximumSize && !values.containsKey(key)) evictOne();
        Entry<V> replaced = values.put(key, new Entry<>(value, expireAt()));
        if (replaced != null) removed(replaced.value());
    }

    public V get(K key) {
        Entry<V> entry = values.get(key);
        if (entry == null) return null;
        if (entry.expireAtNanos() <= System.nanoTime()) {
            if (values.remove(key, entry)) removed(entry.value());
            return null;
        }
        return entry.value();
    }

    public V get(K key, Function<K, V> loader) {
        V current = get(key);
        if (current != null) return current;
        if (values.size() >= maximumSize && !values.containsKey(key)) evictOne();
        Entry<V> entry = values.compute(key, (ignored, existing) -> {
            if (existing != null && existing.expireAtNanos() > System.nanoTime()) return existing;
            if (existing != null) removed(existing.value());
            V loaded = loader.apply(key);
            return loaded == null ? null : new Entry<>(loaded, expireAt());
        });
        return entry == null ? null : entry.value();
    }

    public void invalidate(K key) {
        Entry<V> removed = values.remove(key);
        if (removed != null) removed(removed.value());
    }

    public void shutdown() {
        cleanupTask.cancel(false);
        cleanUp();
        values.clear();
        expiredQueue.clear();
    }

    public long estimatedSize() {
        return values.size();
    }

    public void cleanUp() {
        long now = System.nanoTime();
        values.forEach((key, entry) -> {
            if (entry.expireAtNanos() <= now && values.remove(key, entry)) removed(entry.value());
        });
        if (consumer != null && !expiredQueue.isEmpty()) {
            Queue<V> batch = new ConcurrentLinkedQueue<>();
            V value;
            while ((value = expiredQueue.poll()) != null) batch.add(value);
            if (!batch.isEmpty()) ThreadUtil.submit(() -> consumer.accept(batch));
        }
    }

    private long expireAt() {
        long jitter = randomTtlSeconds == 0 ? 0
                : ThreadLocalRandom.current().nextLong(TimeUnit.SECONDS.toNanos(randomTtlSeconds) + 1);
        return System.nanoTime() + durationNanos + jitter;
    }

    private void evictOne() {
        cleanUp();
        if (values.size() < maximumSize) return;
        values.keySet().stream().findAny().ifPresent(this::invalidate);
    }

    private void removed(V value) {
        if (consumer != null && value != null) expiredQueue.add(value);
    }

    private record Entry<V>(V value, long expireAtNanos) {
    }
}

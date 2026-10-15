package com.yulinlin.data.core.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.lang.util.SegmentLock;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/** Keeps the public key/value API separate from ORM query-cache keys and invalidation. */
public final class DefaultCacheClient implements CacheClient {
    private final QueryCache provider;
    private final SegmentLock locks = new SegmentLock();

    public DefaultCacheClient(QueryCache provider) {
        this.provider = provider == null ? NoOpQueryCache.INSTANCE : provider;
    }

    @Override public boolean available() { return provider.available(); }
    @Override public String providerName() { return provider.providerName(); }

    @Override
    public <T> T get(String namespace, String key, Class<T> type) {
        return value(provider.getApplication(text(namespace, "namespace"), text(key, "key"),
                Objects.requireNonNull(type, "type")));
    }

    @Override
    public <T> T get(String namespace, String key, TypeReference<T> type) {
        Objects.requireNonNull(type, "type");
        return value(provider.getApplication(text(namespace, "namespace"), text(key, "key"), type.getType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(CacheLookup lookup) {
        return lookup.hit() ? (T) lookup.value() : null;
    }

    @Override public void set(String namespace, String key, Object value) {
        set(namespace, key, value, null);
    }

    @Override
    public void set(String namespace, String key, Object value, Duration ttl) {
        provider.putApplication(text(namespace, "namespace"), text(key, "key"),
                Objects.requireNonNull(value, "cache value"), ttl);
    }

    @Override public boolean exists(String namespace, String key) {
        return provider.containsApplication(text(namespace, "namespace"), text(key, "key"));
    }

    @Override public boolean remove(String namespace, String key) {
        return provider.removeApplication(text(namespace, "namespace"), text(key, "key"));
    }

    @Override public void invalidate(String namespace) {
        provider.invalidateApplication(text(namespace, "namespace"));
    }

    @Override public void clear() { provider.clearApplication(); }

    @Override
    public <T> T getOrLoad(String namespace, String key, Class<T> type, Supplier<T> loader) {
        return getOrLoad(namespace, key, type, null, loader);
    }

    @Override
    public <T> T getOrLoad(String namespace, String key, Class<T> type,
                           Duration ttl, Supplier<T> loader) {
        String checkedNamespace = text(namespace, "namespace");
        String checkedKey = text(key, "key");
        T cached = get(checkedNamespace, checkedKey, type);
        if (cached != null) return cached;
        Lock lock = locks.getLock(checkedNamespace + '\u0000' + checkedKey);
        lock.lock();
        try {
            cached = get(checkedNamespace, checkedKey, type);
            if (cached != null) return cached;
            T loaded = Objects.requireNonNull(loader, "loader").get();
            if (loaded != null) set(checkedNamespace, checkedKey, loaded, ttl);
            return loaded;
        } finally {
            lock.unlock();
        }
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}

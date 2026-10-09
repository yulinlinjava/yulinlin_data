package com.yulinlin.data.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCacheProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fast in-process query cache. */
public final class CaffeineQueryCache implements QueryCache {

    private final Cache<CacheKey, Entry> cache;
    private final QueryCacheProperties common;
    private final AtomicLong globalVersion = new AtomicLong();
    private final ConcurrentMap<CacheNamespace, AtomicLong> namespaceVersions = new ConcurrentHashMap<>();

    public CaffeineQueryCache(QueryCacheProperties common, CaffeineCacheProperties properties) {
        this.common = common;
        Caffeine<CacheKey, Entry> builder = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumSize())
                .expireAfter(new Expiry<CacheKey, Entry>() {
                    @Override public long expireAfterCreate(CacheKey key, Entry value, long currentTime) {
                        return value.ttlNanos();
                    }
                    @Override public long expireAfterUpdate(CacheKey key, Entry value,
                                                            long currentTime, long currentDuration) {
                        return value.ttlNanos();
                    }
                    @Override public long expireAfterRead(CacheKey key, Entry value,
                                                          long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                });
        if (properties.isRecordStats()) builder.recordStats();
        this.cache = builder.build();
    }

    @Override
    public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces) {
        return scope(key, namespaces, null);
    }

    @Override
    public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
        LinkedHashMap<CacheNamespace, Long> versions = new LinkedHashMap<>();
        namespaces.stream().sorted().forEach(namespace -> versions.put(namespace,
                namespaceVersions.computeIfAbsent(namespace, ignored -> new AtomicLong()).get()));
        return key.scoped(globalVersion.get(), versions, common.resolveTtl(ttl));
    }

    @Override
    public CacheLookup get(CacheKey key, CacheValueType valueType) {
        Entry entry = cache.getIfPresent(key);
        return entry == null ? CacheLookup.miss() : CacheLookup.hit(entry.value());
    }

    @Override
    public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) {
        cache.put(key, new Entry(value, ttlNanos(common.resolveTtl(ttl))));
    }

    @Override
    public void invalidate(Set<CacheNamespace> namespaces) {
        for (CacheNamespace namespace : namespaces) {
            namespaceVersions.computeIfAbsent(namespace, ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    @Override
    public void invalidateAll() {
        globalVersion.incrementAndGet();
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }

    private static long ttlNanos(Duration ttl) {
        try {
            return ttl.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private record Entry(Object value, long ttlNanos) {
    }
}

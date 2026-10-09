package com.yulinlin.data.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCacheProperties;

import java.util.Objects;

/** Fast in-process query cache. */
public final class CaffeineQueryCache implements QueryCache {

    private final Cache<CacheKey, Entry> cache;

    public CaffeineQueryCache(QueryCacheProperties common, CaffeineCacheProperties properties) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumSize())
                .expireAfterWrite(common.getTtl());
        if (properties.isRecordStats()) builder.recordStats();
        this.cache = builder.build();
    }

    @Override
    public CacheLookup get(CacheKey key, CacheValueType valueType) {
        Entry entry = cache.getIfPresent(key);
        return entry == null ? CacheLookup.miss() : CacheLookup.hit(entry.value());
    }

    @Override
    public void put(CacheKey key, CacheValueType valueType, Object value) {
        cache.put(key, new Entry(value));
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }

    private record Entry(Object value) {
    }
}

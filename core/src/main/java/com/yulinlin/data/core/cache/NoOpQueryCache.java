package com.yulinlin.data.core.cache;

import java.time.Duration;

/** Default behavior when no cache provider module is present. */
public final class NoOpQueryCache implements QueryCache {
    public static final NoOpQueryCache INSTANCE = new NoOpQueryCache();
    private NoOpQueryCache() { }
    @Override public CacheLookup get(CacheKey key, CacheValueType valueType) { return CacheLookup.miss(); }
    @Override public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) { }
}

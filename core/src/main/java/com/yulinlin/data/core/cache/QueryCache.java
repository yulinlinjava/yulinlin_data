package com.yulinlin.data.core.cache;

/** Optional query-result cache implemented by separate provider modules. */
public interface QueryCache extends AutoCloseable {
    CacheLookup get(CacheKey key, CacheValueType valueType);
    void put(CacheKey key, CacheValueType valueType, Object value);
    default String providerName() { return getClass().getSimpleName(); }
    @Override default void close() { }
}

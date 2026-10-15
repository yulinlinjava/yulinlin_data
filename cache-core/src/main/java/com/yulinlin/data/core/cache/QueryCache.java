package com.yulinlin.data.core.cache;

import java.time.Duration;
import java.lang.reflect.Type;
import java.util.Set;

/** Optional query-result cache implemented by separate provider modules. */
public interface QueryCache extends AutoCloseable {
    default boolean available() { return true; }
    /** Whether writes should clear related query-cache namespaces unless a request overrides it. */
    default boolean clearOnWrite() { return available(); }
    /** Adds the provider's current global and namespace versions to a logical query key. */
    default CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces) { return key; }
    /** Also isolates entries that intentionally use different expiry durations. */
    default CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
        return scope(key, namespaces);
    }
    /** Scopes an equality query; providers without partition support retain safe table scoping. */
    default CacheKey scopePartition(CacheKey key, CachePartition partition) {
        return scopePartition(key, partition, null);
    }
    /** Scopes an equality query while preserving an optional per-query expiry identity. */
    default CacheKey scopePartition(CacheKey key, CachePartition partition, Duration ttl) {
        return scope(key, Set.of(partition.namespace()), ttl);
    }
    CacheLookup get(CacheKey key, CacheValueType valueType);
    void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl);
    default void put(CacheKey key, CacheValueType valueType, Object value) {
        put(key, valueType, value, null);
    }
    default void invalidate(Set<CacheNamespace> namespaces) { }
    /** Invalidates exact equality buckets; unsupported providers may rely on the caller's table fallback. */
    default void invalidatePartitions(Set<CachePartition> partitions) { }
    /** Invalidates every equality bucket belonging to the supplied tables. */
    default void invalidatePartitionTables(Set<CacheNamespace> namespaces) { }
    default void invalidateAll() { }
    default CacheLookup getApplication(String namespace, String key, Type valueType) {
        return CacheLookup.miss();
    }
    default void putApplication(String namespace, String key, Object value, Duration ttl) { }
    default boolean containsApplication(String namespace, String key) { return false; }
    default boolean removeApplication(String namespace, String key) { return false; }
    default void invalidateApplication(String namespace) { }
    default void clearApplication() { }
    default String providerName() { return getClass().getSimpleName(); }
    @Override default void close() { }
}

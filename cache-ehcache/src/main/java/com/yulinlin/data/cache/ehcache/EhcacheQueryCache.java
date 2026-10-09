package com.yulinlin.data.cache.ehcache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import org.ehcache.Cache;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.expiry.ExpiryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Set;

/** Ehcache heap + persistent disk query cache. */
public final class EhcacheQueryCache implements QueryCache {

    private static final Logger log = LoggerFactory.getLogger(EhcacheQueryCache.class);
    private static final String CACHE_ALIAS = "yulinlin-query-cache-v2";
    private static final String VERSION_ALIAS = "yulinlin-query-cache-namespace-versions-v1";
    private static final String GLOBAL_VERSION = "@global";

    private final ObjectMapper objectMapper;
    private final QueryCacheProperties common;
    private final PersistentCacheManager manager;
    private final Cache<String, PersistentEntry> cache;
    private final Cache<String, Long> versions;
    private final Object versionLock = new Object();

    public EhcacheQueryCache(QueryCacheProperties common,
                             EhcacheProperties properties,
                             ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.common = common;
        this.manager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(properties.getDirectory().toFile()))
                .withCache(CACHE_ALIAS, CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(String.class, PersistentEntry.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(properties.getHeapEntries(), EntryUnit.ENTRIES)
                                        .disk(properties.getDiskSizeMb(), MemoryUnit.MB, true))
                        .withExpiry(new EntryExpiry()))
                .withCache(VERSION_ALIAS, CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(String.class, Long.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(1024, EntryUnit.ENTRIES)
                                        .disk(10, MemoryUnit.MB, true))
                        .withExpiry(ExpiryPolicyBuilder.noExpiration()))
                .build(true);
        this.cache = manager.getCache(CACHE_ALIAS, String.class, PersistentEntry.class);
        this.versions = manager.getCache(VERSION_ALIAS, String.class, Long.class);
    }

    @Override
    public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces) {
        return scope(key, namespaces, null);
    }

    @Override
    public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
        synchronized (versionLock) {
            LinkedHashMap<CacheNamespace, Long> snapshot = new LinkedHashMap<>();
            namespaces.stream().sorted().forEach(namespace ->
                    snapshot.put(namespace, version(namespace.value())));
            return key.scoped(version(GLOBAL_VERSION), snapshot, common.resolveTtl(ttl));
        }
    }

    @Override
    public CacheLookup get(CacheKey key, CacheValueType valueType) {
        PersistentEntry entry = cache.get(key.value());
        if (entry == null) return CacheLookup.miss();
        try {
            JavaType target = valueType.collection()
                    ? objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, valueType.valueClass())
                    : objectMapper.getTypeFactory().constructType(valueType.valueClass());
            return CacheLookup.hit(objectMapper.readValue(entry.value(), target));
        } catch (Exception error) {
            cache.remove(key.value());
            log.warn("Discarded unreadable query cache entry {}", key.value(), error);
            return CacheLookup.miss();
        }
    }

    @Override
    public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) {
        try {
            cache.put(key.value(), new PersistentEntry(
                    objectMapper.writeValueAsBytes(value), common.resolveTtl(ttl)));
        } catch (Exception error) {
            log.warn("Skipped query cache entry {} because it cannot be serialized", key.value(), error);
        }
    }

    @Override
    public void invalidate(Set<CacheNamespace> namespaces) {
        synchronized (versionLock) {
            for (CacheNamespace namespace : namespaces) increment(namespace.value());
        }
    }

    @Override
    public void invalidateAll() {
        synchronized (versionLock) {
            increment(GLOBAL_VERSION);
        }
    }

    @Override
    public void close() {
        manager.close();
    }

    private long version(String namespace) {
        Long value = versions.get(namespace);
        return value == null ? 0L : value;
    }

    private void increment(String namespace) {
        versions.put(namespace, version(namespace) + 1L);
    }

    private record PersistentEntry(byte[] value, Duration ttl) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    private static final class EntryExpiry implements ExpiryPolicy<String, PersistentEntry>, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        @Override public Duration getExpiryForCreation(String key, PersistentEntry value) { return value.ttl(); }
        @Override public Duration getExpiryForAccess(String key,
                                                     java.util.function.Supplier<? extends PersistentEntry> value) {
            return null;
        }
        @Override public Duration getExpiryForUpdate(String key,
                                                     java.util.function.Supplier<? extends PersistentEntry> oldValue,
                                                     PersistentEntry newValue) {
            return newValue.ttl();
        }
    }
}

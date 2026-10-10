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
import org.ehcache.config.builders.PooledExecutionServiceConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.expiry.ExpiryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/** Ehcache heap + persistent disk query cache. */
public final class EhcacheQueryCache implements QueryCache {

    private static final Logger log = LoggerFactory.getLogger(EhcacheQueryCache.class);
    private static final String CACHE_ALIAS = "yulinlin-query-cache-v2";
    private static final String VERSION_ALIAS = "yulinlin-query-cache-namespace-versions-v1";
    private static final String GLOBAL_VERSION = "@global";
    private static final String APPLICATION_GLOBAL_VERSION = "@application:global";
    private static final String APPLICATION_NAMESPACE_PREFIX = "@application:namespace:";
    private static final String DISK_POOL_ALIAS = "yulinlin-cache-disk";

    private final ObjectMapper objectMapper;
    private final QueryCacheProperties common;
    private final PersistentCacheManager manager;
    private final Cache<String, PersistentEntry> cache;
    private final Cache<String, Long> versions;
    private final Object versionLock = new Object();
    private final long maximumEntryBytes;
    private final boolean recordStatistics;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder puts = new LongAdder();
    private final LongAdder oversizedEntries = new LongAdder();
    private final LongAdder serializationFailures = new LongAdder();

    public EhcacheQueryCache(QueryCacheProperties common,
                             EhcacheProperties properties,
                             ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.common = common;
        this.maximumEntryBytes = megabytesToBytes(properties.getMaximumEntrySizeMb());
        this.recordStatistics = properties.isRecordStatistics();
        ResourcePoolsBuilder resources = ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(properties.getHeapEntries(), EntryUnit.ENTRIES);
        if (properties.getOffheapSizeMb() > 0) {
            resources = resources.offheap(properties.getOffheapSizeMb(), MemoryUnit.MB);
        }
        resources = resources.disk(properties.getDiskSizeMb(), MemoryUnit.MB, properties.isPersistent());
        this.manager = CacheManagerBuilder.newCacheManagerBuilder()
                .using(PooledExecutionServiceConfigurationBuilder
                        .newPooledExecutionServiceConfigurationBuilder()
                        .defaultPool(DISK_POOL_ALIAS, properties.getDiskThreads(), properties.getDiskThreads())
                        .build())
                .withDefaultDiskStoreThreadPool(DISK_POOL_ALIAS)
                .with(CacheManagerBuilder.persistence(properties.getDirectory().toFile()))
                .withCache(CACHE_ALIAS, CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(String.class, PersistentEntry.class,
                                resources)
                        .withExpiry(new EntryExpiry(properties.getExpirationPolicy())))
                .withCache(VERSION_ALIAS, CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(String.class, Long.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(1024, EntryUnit.ENTRIES)
                                        .disk(10, MemoryUnit.MB, properties.isPersistent()))
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
            return key.scoped(version(GLOBAL_VERSION), snapshot, common.resolveKeyTtl(ttl));
        }
    }

    @Override
    public CacheLookup get(CacheKey key, CacheValueType valueType) {
        PersistentEntry entry = cache.get(key.value());
        if (entry == null) {
            record(misses);
            return CacheLookup.miss();
        }
        try {
            JavaType target = valueType.collection()
                    ? objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, valueType.valueClass())
                    : objectMapper.getTypeFactory().constructType(valueType.valueClass());
            Object value = objectMapper.readValue(entry.value(), target);
            record(hits);
            return CacheLookup.hit(value);
        } catch (Exception error) {
            cache.remove(key.value());
            record(misses);
            record(serializationFailures);
            log.warn("Discarded unreadable query cache entry {}", key.value(), error);
            return CacheLookup.miss();
        }
    }

    @Override
    public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) {
        putEntry(key.value(), value, ttl, "query");
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
    public CacheLookup getApplication(String namespace, String key, Type valueType) {
        String physicalKey = applicationKey(namespace, key).value();
        PersistentEntry entry = cache.get(physicalKey);
        if (entry == null) {
            record(misses);
            return CacheLookup.miss();
        }
        try {
            Object value = objectMapper.readValue(entry.value(),
                    objectMapper.getTypeFactory().constructType(valueType));
            record(hits);
            return CacheLookup.hit(value);
        } catch (Exception error) {
            cache.remove(physicalKey);
            record(misses);
            record(serializationFailures);
            log.warn("Discarded unreadable application cache entry {}", physicalKey, error);
            return CacheLookup.miss();
        }
    }

    @Override
    public void putApplication(String namespace, String key, Object value, Duration ttl) {
        String physicalKey = applicationKey(namespace, key).value();
        putEntry(physicalKey, value, ttl, "application");
    }

    private void putEntry(String physicalKey, Object value, Duration ttl, String kind) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            if (bytes.length > maximumEntryBytes) {
                record(oversizedEntries);
                log.warn("Skipped oversized {} cache entry {}: {} bytes exceeds {} bytes",
                        kind, physicalKey, bytes.length, maximumEntryBytes);
                return;
            }
            cache.put(physicalKey, new PersistentEntry(bytes, common.resolveTtl(ttl)));
            record(puts);
        } catch (Exception error) {
            record(serializationFailures);
            log.warn("Skipped {} cache entry {} because it cannot be serialized", kind, physicalKey, error);
        }
    }

    @Override
    public boolean containsApplication(String namespace, String key) {
        return cache.containsKey(applicationKey(namespace, key).value());
    }

    @Override
    public boolean removeApplication(String namespace, String key) {
        String physicalKey = applicationKey(namespace, key).value();
        boolean present = cache.containsKey(physicalKey);
        if (present) cache.remove(physicalKey);
        return present;
    }

    @Override
    public void invalidateApplication(String namespace) {
        synchronized (versionLock) {
            increment(APPLICATION_NAMESPACE_PREFIX + namespace);
        }
    }

    @Override
    public void clearApplication() {
        synchronized (versionLock) {
            increment(APPLICATION_GLOBAL_VERSION);
        }
    }

    public Stats stats() {
        return new Stats(hits.sum(), misses.sum(), puts.sum(),
                oversizedEntries.sum(), serializationFailures.sum());
    }

    public void resetStatistics() {
        hits.reset();
        misses.reset();
        puts.reset();
        oversizedEntries.reset();
        serializationFailures.reset();
    }

    private CacheKey applicationKey(String namespace, String key) {
        synchronized (versionLock) {
            return CacheKey.application(namespace, key,
                    version(APPLICATION_GLOBAL_VERSION),
                    version(APPLICATION_NAMESPACE_PREFIX + namespace));
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

    private void record(LongAdder counter) {
        if (recordStatistics) counter.increment();
    }

    private static long megabytesToBytes(long megabytes) {
        try {
            return Math.multiplyExact(megabytes, 1024L * 1024L);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("maximum-entry-size-mb is too large", error);
        }
    }

    public record Stats(long hits, long misses, long puts,
                        long oversizedEntries, long serializationFailures) {
    }

    private record PersistentEntry(byte[] value, Duration ttl) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    private static final class EntryExpiry implements ExpiryPolicy<String, PersistentEntry>, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private final EhcacheProperties.ExpirationPolicy policy;

        private EntryExpiry(EhcacheProperties.ExpirationPolicy policy) {
            this.policy = policy;
        }

        @Override public Duration getExpiryForCreation(String key, PersistentEntry value) { return value.ttl(); }
        @Override public Duration getExpiryForAccess(String key,
                                                     java.util.function.Supplier<? extends PersistentEntry> value) {
            if (policy != EhcacheProperties.ExpirationPolicy.AFTER_ACCESS) return null;
            PersistentEntry entry = value.get();
            return entry == null ? null : entry.ttl();
        }
        @Override public Duration getExpiryForUpdate(String key,
                                                     java.util.function.Supplier<? extends PersistentEntry> oldValue,
                                                     PersistentEntry newValue) {
            return newValue.ttl();
        }
    }
}

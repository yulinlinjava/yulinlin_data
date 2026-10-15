package com.yulinlin.data.cache.ehcache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CachePartition;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private static final int MAX_PARTITION_VALUES_PER_TABLE = 4096;

    private final EhcacheValueCodec codec;
    private final QueryCacheProperties common;
    private final PersistentCacheManager manager;
    private final Cache<String, byte[]> cache;
    private final Cache<String, Long> versions;
    private final Object versionLock = new Object();
    private final Map<CacheNamespace, PartitionState> partitionStates = new HashMap<>();
    private long partitionRuntimeToken = newRuntimeToken();
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
        this.codec = new EhcacheValueCodec(objectMapper, properties.getSerializer());
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
                        .newCacheConfigurationBuilder(String.class, byte[].class,
                                resources)
                        .withExpiry(new EntryExpiry(properties.getExpirationPolicy())))
                .withCache(VERSION_ALIAS, CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(String.class, Long.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(1024, EntryUnit.ENTRIES)
                                        .disk(10, MemoryUnit.MB, properties.isPersistent()))
                        .withExpiry(ExpiryPolicyBuilder.noExpiration()))
                .build(true);
        this.cache = manager.getCache(CACHE_ALIAS, String.class, byte[].class);
        this.versions = manager.getCache(VERSION_ALIAS, String.class, Long.class);
    }

    @Override
    public boolean clearOnWrite() {
        return common.isClearOnWrite();
    }

    @Override
    public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces) {
        return scope(key, namespaces, null);
    }

    @Override
    public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
        synchronized (versionLock) {
            return scopeLocked(key, namespaces, ttl);
        }
    }

    @Override
    public CacheKey scopePartition(CacheKey key, CachePartition partition, Duration ttl) {
        synchronized (versionLock) {
            PartitionState state = partitionStates.computeIfAbsent(
                    partition.namespace(), ignored -> new PartitionState());
            if (state.table) {
                return scopeLocked(key, Set.of(partition.namespace()), ttl);
            }
            if (state.field == null) {
                state.field = partition.field();
            } else if (!state.field.equals(partition.field())) {
                state.upgradeToTable();
                return scopeLocked(key, Set.of(partition.namespace()), ttl);
            }
            return key.scopedPartition(partitionRuntimeToken, state.tableVersion,
                    state.partitionVersion(partition.valueKey()), common.resolveKeyTtl(ttl));
        }
    }

    @Override
    public CacheLookup get(CacheKey key, CacheValueType valueType) {
        byte[] entry = cache.get(key.value());
        if (entry == null) {
            record(misses);
            return CacheLookup.miss();
        }
        try {
            Object value = codec.decode(entry, valueType).value();
            record(hits);
            return CacheLookup.hit(value);
        } catch (Exception error) {
            cache.remove(key.value());
            record(misses);
            record(serializationFailures);
            log.warn("查询缓存条目无法读取，已丢弃 key={}", key.value(), error);
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
    public void invalidatePartitions(Set<CachePartition> partitions) {
        synchronized (versionLock) {
            for (CachePartition partition : partitions) {
                PartitionState state = partitionStates.get(partition.namespace());
                // A write must not choose the table's cache field before the first cached query does.
                if (state == null || state.table) continue;
                if (!state.field.equals(partition.field())) {
                    state.upgradeToTable();
                    continue;
                }
                state.increment(partition.valueKey());
            }
        }
    }

    @Override
    public void invalidatePartitionTables(Set<CacheNamespace> namespaces) {
        synchronized (versionLock) {
            for (CacheNamespace namespace : namespaces) {
                PartitionState state = partitionStates.get(namespace);
                // With no partition reads yet there is nothing to invalidate or disable.
                if (state != null && !state.table) state.rotateTable();
            }
        }
    }

    @Override
    public void invalidateAll() {
        synchronized (versionLock) {
            increment(GLOBAL_VERSION);
            partitionRuntimeToken = newRuntimeToken();
            partitionStates.clear();
        }
    }

    @Override
    public CacheLookup getApplication(String namespace, String key, Type valueType) {
        String physicalKey = applicationKey(namespace, key).value();
        byte[] entry = cache.get(physicalKey);
        if (entry == null) {
            record(misses);
            return CacheLookup.miss();
        }
        try {
            Object value = codec.decode(entry, valueType).value();
            record(hits);
            return CacheLookup.hit(value);
        } catch (Exception error) {
            cache.remove(physicalKey);
            record(misses);
            record(serializationFailures);
            log.warn("业务缓存条目无法读取，已丢弃 key={}", physicalKey, error);
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
            byte[] bytes = codec.encode(value, common.resolveTtl(ttl));
            if (bytes.length > maximumEntryBytes) {
                record(oversizedEntries);
                log.warn("缓存条目超过大小限制，已跳过 kind={} key={} sizeBytes={} limitBytes={}",
                        kind, physicalKey, bytes.length, maximumEntryBytes);
                return;
            }
            cache.put(physicalKey, bytes);
            record(puts);
        } catch (Exception error) {
            record(serializationFailures);
            log.warn("缓存条目无法序列化，已跳过 kind={} key={}", kind, physicalKey, error);
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

    private CacheKey scopeLocked(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
        LinkedHashMap<CacheNamespace, Long> snapshot = new LinkedHashMap<>();
        namespaces.stream().sorted().forEach(namespace ->
                snapshot.put(namespace, version(namespace.value())));
        return key.scoped(version(GLOBAL_VERSION), snapshot, common.resolveKeyTtl(ttl));
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

    private static long newRuntimeToken() {
        UUID value = UUID.randomUUID();
        return value.getMostSignificantBits() ^ value.getLeastSignificantBits();
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

    private static final class PartitionState {
        private final Map<String, Long> versions = new HashMap<>();
        private String field;
        private boolean table;
        private long tableVersion;

        private long partitionVersion(String valueKey) {
            Long version = versions.get(valueKey);
            if (version != null) return version;
            if (versions.size() >= MAX_PARTITION_VALUES_PER_TABLE) rotateTable();
            versions.put(valueKey, 0L);
            return 0L;
        }

        private void increment(String valueKey) {
            versions.computeIfPresent(valueKey, (ignored, version) -> version + 1L);
        }

        private void upgradeToTable() {
            table = true;
            field = null;
            rotateTable();
        }

        private void rotateTable() {
            tableVersion++;
            versions.clear();
        }
    }

    private static final class EntryExpiry implements ExpiryPolicy<String, byte[]>, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private final EhcacheProperties.ExpirationPolicy policy;

        private EntryExpiry(EhcacheProperties.ExpirationPolicy policy) {
            this.policy = policy;
        }

        @Override public Duration getExpiryForCreation(String key, byte[] value) {
            return EhcacheValueCodec.ttl(value);
        }
        @Override public Duration getExpiryForAccess(String key,
                                                     java.util.function.Supplier<? extends byte[]> value) {
            if (policy != EhcacheProperties.ExpirationPolicy.AFTER_ACCESS) return null;
            byte[] entry = value.get();
            return entry == null ? null : EhcacheValueCodec.ttl(entry);
        }
        @Override public Duration getExpiryForUpdate(String key,
                                                     java.util.function.Supplier<? extends byte[]> oldValue,
                                                     byte[] newValue) {
            return EhcacheValueCodec.ttl(newValue);
        }
    }
}

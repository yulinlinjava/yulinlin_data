package com.yulinlin.data.cache.redis;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Redis L2 query cache with an optional decoded-object Caffeine L1. */
public final class RedisQueryCache implements QueryCache {
    @Override public String providerName() { return "redis"; }

    private static final Logger log = LoggerFactory.getLogger(RedisQueryCache.class);
    private static final String GLOBAL_VERSION = "@global";
    private static final String APPLICATION_GLOBAL_VERSION = "@application:global";
    private static final String APPLICATION_NAMESPACE_PREFIX = "@application:namespace:";
    private static final String PARTITION_TABLE = "@table";
    private static final String PARTITION_MODE = "mode";
    private static final String PARTITION_TABLE_VERSION = "table";
    private static final int MAX_PARTITION_VALUES = 4096;
    private static final int ASYNC_QUEUE_CAPACITY = 4096;

    private final QueryCacheProperties common;
    private final RedisCacheProperties properties;
    private final RedisValueCodec codec;
    private final RedisTemplate<String, byte[]> values;
    private final StringRedisTemplate metadata;
    private final HashOperations<String, String, String> hashes;
    private final Cache<String, LocalEntry> local;
    private final long maximumEntryBytes;
    private final String queryPrefix;
    private final String applicationPrefix;
    private final String versionKey;
    private final String partitionPrefix;
    private final boolean recordStatistics;
    private final Object redisMutationLock = new Object();
    private final ConcurrentMap<String, AtomicLong> localVersions = new ConcurrentHashMap<>();
    private final ConcurrentMap<CacheNamespace, LocalPartitionState> localPartitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> writeEpochs = new ConcurrentHashMap<>();
    private final AtomicLong writeSequence = new AtomicLong();
    private final AtomicLong failureToken = new AtomicLong(System.nanoTime());
    private final AtomicLong lastDropWarningNanos = new AtomicLong();
    private final ArrayBlockingQueue<WriteTask> writeQueue;
    private final Thread writeWorker;
    private volatile boolean acceptingWrites = true;

    private final LongAdder localHits = new LongAdder();
    private final LongAdder redisHits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder puts = new LongAdder();
    private final LongAdder oversizedEntries = new LongAdder();
    private final LongAdder serializationFailures = new LongAdder();
    private final LongAdder redisFailures = new LongAdder();
    private final LongAdder asynchronousDrops = new LongAdder();

    public RedisQueryCache(QueryCacheProperties common,
                           RedisCacheProperties properties,
                           RedisConnectionFactory connectionFactory,
                           com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.common = Objects.requireNonNull(common, "common");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.codec = new RedisValueCodec(objectMapper, properties.getSerializer());
        this.maximumEntryBytes = megabytesToBytes(properties.getMaximumEntrySizeMb());
        this.recordStatistics = properties.isRecordStatistics();

        String prefix = properties.getKeyPrefix();
        this.queryPrefix = prefix + ":q:";
        this.applicationPrefix = prefix + ":a:";
        this.versionKey = prefix + ":meta:versions";
        this.partitionPrefix = prefix + ":meta:partition:";

        this.values = valueTemplate(connectionFactory);
        this.metadata = new StringRedisTemplate(connectionFactory);
        this.metadata.afterPropertiesSet();
        this.hashes = metadata.opsForHash();
        this.local = properties.getCaffeine().isEnabled()
                ? localCache(properties.getCaffeine().getMaximumSize(), properties.isRecordStatistics())
                : null;

        if (properties.getWriteMode() == RedisWriteMode.ASYNC) {
            this.writeQueue = new ArrayBlockingQueue<>(ASYNC_QUEUE_CAPACITY);
            this.writeWorker = Thread.ofVirtual()
                    .name("yulinlin-redis-cache-writer")
                    .start(this::consumeWrites);
        } else {
            this.writeQueue = null;
            this.writeWorker = null;
        }
    }

    @Override
    public boolean clearOnWrite() {
        return common.isClearOnWrite();
    }

    @Override
    public Duration resolveTtl(Duration requested, Set<CacheNamespace> namespaces) {
        return common.resolveTtl(requested, namespaces);
    }

    @Override
    public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
        VersionRead global = version(GLOBAL_VERSION);
        if (!global.success()) return failureScope(key, ttl);
        LinkedHashMap<CacheNamespace, Long> snapshot = new LinkedHashMap<>();
        for (CacheNamespace namespace : namespaces.stream().sorted().toList()) {
            VersionRead value = version(namespace.value());
            if (!value.success()) return failureScope(key, ttl);
            snapshot.put(namespace, value.value());
        }
        return key.scoped(global.value(), snapshot, common.resolveKeyTtl(ttl));
    }

    @Override
    public CacheKey scopePartition(CacheKey key, CachePartition partition, Duration ttl) {
        VersionRead global = version(GLOBAL_VERSION);
        if (!global.success()) return failureScope(key, ttl);
        PartitionRead snapshot = local == null
                ? remotePartition(partition)
                : localPartition(partition);
        if (!snapshot.success()) return failureScope(key, ttl);
        if (snapshot.table()) return scope(key, Set.of(partition.namespace()), ttl);
        return key.scopedPartition(global.value(), snapshot.tableVersion(),
                snapshot.partitionVersion(), common.resolveKeyTtl(ttl));
    }

    @Override
    public CacheLookup get(CacheKey key, CacheValueType valueType) {
        return getEntry(queryPrefix + key.value(), bytes -> codec.decode(bytes, valueType));
    }

    @Override
    public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) {
        putEntry(queryPrefix + key.value(), value, ttl, "query");
    }

    @Override
    public void invalidate(Set<CacheNamespace> namespaces) {
        for (CacheNamespace namespace : namespaces) incrementVersion(namespace.value());
    }

    @Override
    public void invalidatePartitions(Set<CachePartition> partitions) {
        for (CachePartition partition : partitions) {
            remoteInvalidatePartition(partition);
            LocalPartitionState state = localPartitions.get(partition.namespace());
            if (state == null) continue;
            synchronized (state) {
                if (state.table || state.field == null) continue;
                if (!state.field.equals(partition.field())) {
                    state.toTable();
                } else {
                    state.versions.computeIfPresent(partition.valueKey(),
                            (ignored, version) -> version + 1L);
                }
            }
        }
    }

    @Override
    public void invalidatePartitionTables(Set<CacheNamespace> namespaces) {
        for (CacheNamespace namespace : namespaces) {
            remoteInvalidatePartitionTable(namespace);
            LocalPartitionState state = localPartitions.get(namespace);
            if (state == null) continue;
            synchronized (state) {
                if (!state.table && state.field != null) state.rotateTable();
            }
        }
    }

    @Override
    public void invalidateAll() {
        incrementVersion(GLOBAL_VERSION);
        localPartitions.clear();
    }

    @Override
    public CacheLookup getApplication(String namespace, String key, Type valueType) {
        CacheKey cacheKey = applicationKey(namespace, key);
        return getEntry(applicationPrefix + cacheKey.value(), bytes -> codec.decode(bytes, valueType));
    }

    @Override
    public void putApplication(String namespace, String key, Object value, Duration ttl) {
        putEntry(applicationPrefix + applicationKey(namespace, key).value(), value, ttl, "application");
    }

    @Override
    public boolean containsApplication(String namespace, String key) {
        String physicalKey = applicationPrefix + applicationKey(namespace, key).value();
        if (local != null && local.getIfPresent(physicalKey) != null) return true;
        try {
            return Boolean.TRUE.equals(values.hasKey(physicalKey));
        } catch (RuntimeException error) {
            redisFailure("检查业务缓存失败", physicalKey, error);
            return false;
        }
    }

    @Override
    public boolean removeApplication(String namespace, String key) {
        String physicalKey = applicationPrefix + applicationKey(namespace, key).value();
        boolean localPresent = local != null && local.asMap().remove(physicalKey) != null;
        synchronized (redisMutationLock) {
            writeEpochs.put(physicalKey, writeSequence.incrementAndGet());
            if (writeQueue != null) writeQueue.removeIf(task -> task.key().equals(physicalKey));
            try {
                return Boolean.TRUE.equals(values.delete(physicalKey)) || localPresent;
            } catch (RuntimeException error) {
                redisFailure("删除业务缓存失败", physicalKey, error);
                return localPresent;
            }
        }
    }

    @Override
    public void invalidateApplication(String namespace) {
        incrementVersion(APPLICATION_NAMESPACE_PREFIX + namespace);
    }

    @Override
    public void clearApplication() {
        incrementVersion(APPLICATION_GLOBAL_VERSION);
    }

    public Stats stats() {
        return new Stats(localHits.sum(), redisHits.sum(), misses.sum(), puts.sum(),
                oversizedEntries.sum(), serializationFailures.sum(), redisFailures.sum(),
                asynchronousDrops.sum());
    }

    public void resetStatistics() {
        localHits.reset();
        redisHits.reset();
        misses.reset();
        puts.reset();
        oversizedEntries.reset();
        serializationFailures.reset();
        redisFailures.reset();
        asynchronousDrops.reset();
    }

    public long estimatedLocalSize() {
        return local == null ? 0L : local.estimatedSize();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats localStats() {
        return local == null
                ? com.github.benmanes.caffeine.cache.stats.CacheStats.empty()
                : local.stats();
    }

    @Override
    public void close() {
        acceptingWrites = false;
        if (writeWorker == null) return;
        writeWorker.interrupt();
        try {
            writeWorker.join(Duration.ofSeconds(5));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        if (writeWorker.isAlive()) {
            writeWorker.interrupt();
            log.warn("Redis 缓存异步写线程未在 5 秒内完成，剩余任务数={}", writeQueue.size());
        }
    }

    private CacheLookup getEntry(String physicalKey, Decoder decoder) {
        if (local != null) {
            LocalEntry entry = local.getIfPresent(physicalKey);
            if (entry != null) {
                record(localHits);
                return CacheLookup.hit(entry.value());
            }
        }
        byte[] bytes;
        try {
            bytes = values.opsForValue().get(physicalKey);
        } catch (RuntimeException error) {
            redisFailure("读取缓存失败", physicalKey, error);
            record(misses);
            return CacheLookup.miss();
        }
        if (bytes == null) {
            record(misses);
            return CacheLookup.miss();
        }
        try {
            RedisValueCodec.Decoded decoded = decoder.decode(bytes);
            if (local != null) local.put(physicalKey,
                    new LocalEntry(decoded.value(), ttlNanos(decoded.ttl())));
            record(redisHits);
            return CacheLookup.hit(decoded.value());
        } catch (Exception error) {
            record(serializationFailures);
            record(misses);
            if (local != null) local.invalidate(physicalKey);
            synchronized (redisMutationLock) {
                try {
                    values.delete(physicalKey);
                } catch (RuntimeException deleteError) {
                    redisFailure("删除损坏缓存失败", physicalKey, deleteError);
                }
            }
            log.warn("缓存条目无法反序列化，已丢弃 key={}", physicalKey, error);
            return CacheLookup.miss();
        }
    }

    private void putEntry(String physicalKey, Object value, Duration requestedTtl, String kind) {
        Duration ttl = common.resolveTtl(requestedTtl);
        byte[] encoded;
        try {
            encoded = codec.encode(value, ttl);
        } catch (Exception error) {
            record(serializationFailures);
            log.warn("缓存条目无法序列化，已跳过 kind={} key={}", kind, physicalKey, error);
            return;
        }
        if (encoded.length > maximumEntryBytes) {
            record(oversizedEntries);
            log.warn("缓存条目超过大小限制，已跳过 kind={} key={} sizeBytes={} limitBytes={}",
                    kind, physicalKey, encoded.length, maximumEntryBytes);
            return;
        }
        if (local != null) local.put(physicalKey, new LocalEntry(value, ttlNanos(ttl)));
        record(puts);
        if (writeQueue == null) {
            writeSynchronously(new WriteTask(physicalKey, encoded, ttl, 0L));
            return;
        }
        long epoch = writeSequence.incrementAndGet();
        writeEpochs.put(physicalKey, epoch);
        if (!acceptingWrites || !writeQueue.offer(new WriteTask(physicalKey, encoded, ttl, epoch))) {
            writeEpochs.remove(physicalKey, epoch);
            record(asynchronousDrops);
            warnAsynchronousDrop(physicalKey);
        }
    }

    private void writeSynchronously(WriteTask task) {
        synchronized (redisMutationLock) {
            try {
                values.opsForValue().set(task.key(), task.value(), task.ttl());
            } catch (RuntimeException error) {
                redisFailure("写入缓存失败", task.key(), error);
            }
        }
    }

    private void consumeWrites() {
        ArrayList<WriteTask> buffer = new ArrayList<>(properties.getBatchSize());
        while (acceptingWrites || !writeQueue.isEmpty()) {
            try {
                WriteTask first = acceptingWrites
                        ? writeQueue.poll(250, TimeUnit.MILLISECONDS)
                        : writeQueue.poll();
                if (first == null) continue;
                buffer.add(first);
                writeQueue.drainTo(buffer, properties.getBatchSize() - 1);
                writePipeline(buffer);
                buffer.clear();
            } catch (InterruptedException ignored) {
                // close() interrupts the poll so the remaining queue can be drained immediately.
            } catch (RuntimeException error) {
                record(redisFailures);
                log.warn("Redis 缓存异步批量写入失败 batchSize={}", buffer.size(), error);
                buffer.clear();
            }
        }
    }

    private void writePipeline(List<WriteTask> tasks) {
        LinkedHashMap<String, WriteTask> latest = new LinkedHashMap<>();
        for (WriteTask task : tasks) latest.put(task.key(), task);
        synchronized (redisMutationLock) {
            List<WriteTask> current = latest.values().stream()
                    .filter(task -> writeEpochs.getOrDefault(task.key(), 0L) == task.epoch())
                    .toList();
            if (current.isEmpty()) return;
            StringRedisSerializer keySerializer = StringRedisSerializer.UTF_8;
            try {
                values.executePipelined((RedisCallback<Object>) connection -> {
                    for (WriteTask task : current) {
                        connection.stringCommands().set(
                                keySerializer.serialize(task.key()), task.value(),
                                Expiration.from(task.ttl()), RedisStringCommands.SetOption.UPSERT);
                    }
                    return null;
                }, RedisSerializer.byteArray());
            } finally {
                for (WriteTask task : current) writeEpochs.remove(task.key(), task.epoch());
            }
        }
    }

    private CacheKey applicationKey(String namespace, String key) {
        VersionRead global = version(APPLICATION_GLOBAL_VERSION);
        VersionRead localNamespace = version(APPLICATION_NAMESPACE_PREFIX + namespace);
        if (!global.success() || !localNamespace.success()) {
            long token = failureToken.incrementAndGet();
            return CacheKey.application(namespace, key, token, token);
        }
        return CacheKey.application(namespace, key, global.value(), localNamespace.value());
    }

    private VersionRead version(String field) {
        if (local == null) return remoteVersion(field);
        AtomicLong present = localVersions.get(field);
        if (present != null) return new VersionRead(true, present.get());
        VersionRead remote = remoteVersion(field);
        if (!remote.success()) return remote;
        AtomicLong created = new AtomicLong(remote.value());
        AtomicLong raced = localVersions.putIfAbsent(field, created);
        return new VersionRead(true, (raced == null ? created : raced).get());
    }

    private VersionRead remoteVersion(String field) {
        try {
            String value = hashes.get(versionKey, field);
            return new VersionRead(true, value == null ? 0L : Long.parseLong(value));
        } catch (RuntimeException error) {
            redisFailure("读取缓存版本失败", field, error);
            return new VersionRead(false, 0L);
        }
    }

    private void incrementVersion(String field) {
        Long remote = null;
        try {
            remote = hashes.increment(versionKey, field, 1L);
        } catch (RuntimeException error) {
            redisFailure("更新缓存版本失败", field, error);
        }
        if (local == null) return;
        AtomicLong value = localVersions.get(field);
        if (value == null) {
            if (remote != null) localVersions.putIfAbsent(field, new AtomicLong(remote));
        } else {
            Long remoteVersion = remote;
            value.updateAndGet(current -> remoteVersion == null
                    ? current + 1L
                    : Math.max(current + 1L, remoteVersion));
        }
    }

    private PartitionRead localPartition(CachePartition partition) {
        LocalPartitionState state = localPartitions.computeIfAbsent(
                partition.namespace(), ignored -> new LocalPartitionState());
        synchronized (state) {
            if (state.table) return PartitionRead.table(state.tableVersion);
            Long version = state.versions.get(partition.valueKey());
            if (state.field != null && state.field.equals(partition.field()) && version != null) {
                return PartitionRead.partition(state.tableVersion, version);
            }
            PartitionRead remote = remotePartition(partition);
            if (!remote.success()) return remote;
            if (remote.table()) {
                state.table = true;
                state.field = null;
                state.tableVersion = remote.tableVersion();
                state.versions.clear();
            } else {
                state.field = partition.field();
                state.tableVersion = remote.tableVersion();
                state.versions.put(partition.valueKey(), remote.partitionVersion());
            }
            return remote;
        }
    }

    private PartitionRead remotePartition(CachePartition partition) {
        String metaKey = partitionMetaKey(partition.namespace());
        String valuesKey = partitionValuesKey(partition.namespace());
        try {
            String mode = hashes.get(metaKey, PARTITION_MODE);
            if (mode == null) {
                hashes.putIfAbsent(metaKey, PARTITION_MODE, partition.field());
                mode = hashes.get(metaKey, PARTITION_MODE);
            }
            long tableVersion = number(hashes.get(metaKey, PARTITION_TABLE_VERSION));
            if (PARTITION_TABLE.equals(mode)) return PartitionRead.table(tableVersion);
            if (!partition.field().equals(mode)) {
                hashes.put(metaKey, PARTITION_MODE, PARTITION_TABLE);
                Long changed = hashes.increment(metaKey, PARTITION_TABLE_VERSION, 1L);
                metadata.delete(valuesKey);
                return PartitionRead.table(changed == null ? tableVersion + 1L : changed);
            }

            String value = hashes.get(valuesKey, partition.valueKey());
            if (value == null) {
                Long size = hashes.size(valuesKey);
                if (size != null && size >= MAX_PARTITION_VALUES) {
                    Long changed = hashes.increment(metaKey, PARTITION_TABLE_VERSION, 1L);
                    metadata.delete(valuesKey);
                    tableVersion = changed == null ? tableVersion + 1L : changed;
                }
                hashes.putIfAbsent(valuesKey, partition.valueKey(), "0");
                value = hashes.get(valuesKey, partition.valueKey());
            }
            return PartitionRead.partition(tableVersion, number(value));
        } catch (RuntimeException error) {
            redisFailure("读取缓存分区版本失败", partition.namespace().value(), error);
            return PartitionRead.failure();
        }
    }

    private void remoteInvalidatePartition(CachePartition partition) {
        String metaKey = partitionMetaKey(partition.namespace());
        String valuesKey = partitionValuesKey(partition.namespace());
        try {
            String mode = hashes.get(metaKey, PARTITION_MODE);
            if (mode == null || PARTITION_TABLE.equals(mode)) return;
            if (partition.field().equals(mode)) {
                if (Boolean.TRUE.equals(hashes.hasKey(valuesKey, partition.valueKey()))) {
                    hashes.increment(valuesKey, partition.valueKey(), 1L);
                }
            } else {
                hashes.put(metaKey, PARTITION_MODE, PARTITION_TABLE);
                hashes.increment(metaKey, PARTITION_TABLE_VERSION, 1L);
                metadata.delete(valuesKey);
            }
        } catch (RuntimeException error) {
            redisFailure("更新缓存分区版本失败", partition.namespace().value(), error);
        }
    }

    private void remoteInvalidatePartitionTable(CacheNamespace namespace) {
        String metaKey = partitionMetaKey(namespace);
        try {
            String mode = hashes.get(metaKey, PARTITION_MODE);
            if (mode == null || PARTITION_TABLE.equals(mode)) return;
            hashes.increment(metaKey, PARTITION_TABLE_VERSION, 1L);
            metadata.delete(partitionValuesKey(namespace));
        } catch (RuntimeException error) {
            redisFailure("更新表分区版本失败", namespace.value(), error);
        }
    }

    private CacheKey failureScope(CacheKey key, Duration ttl) {
        return key.scoped(failureToken.incrementAndGet(), Map.of(), common.resolveKeyTtl(ttl));
    }

    private String partitionMetaKey(CacheNamespace namespace) {
        return partitionPrefix + safe(namespace.value()) + ":state";
    }

    private String partitionValuesKey(CacheNamespace namespace) {
        return partitionPrefix + safe(namespace.value()) + ":values";
    }

    private static String safe(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void redisFailure(String message, String key, RuntimeException error) {
        record(redisFailures);
        log.warn("{} key={}", message, key, error);
    }

    private void warnAsynchronousDrop(String key) {
        long now = System.nanoTime();
        long previous = lastDropWarningNanos.get();
        if (previous != 0L && now - previous < Duration.ofMinutes(1).toNanos()) return;
        if (lastDropWarningNanos.compareAndSet(previous, now)) {
            log.warn("Redis 缓存异步写队列已满或正在关闭，已跳过 L2 写入 key={} queueSize={}",
                    key, writeQueue.size());
        }
    }

    private void record(LongAdder counter) {
        if (recordStatistics) counter.increment();
    }

    private static long number(String value) {
        return value == null ? 0L : Long.parseLong(value);
    }

    private static long ttlNanos(Duration ttl) {
        try {
            return ttl.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long megabytesToBytes(long megabytes) {
        try {
            return Math.multiplyExact(megabytes, 1024L * 1024L);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("maximum-entry-size-mb is too large", error);
        }
    }

    private static RedisTemplate<String, byte[]> valueTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    private static Cache<String, LocalEntry> localCache(long maximumSize, boolean recordStats) {
        Caffeine<String, LocalEntry> builder = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new Expiry<String, LocalEntry>() {
                    @Override public long expireAfterCreate(String key, LocalEntry value, long currentTime) {
                        return value.ttlNanos();
                    }
                    @Override public long expireAfterUpdate(String key, LocalEntry value,
                                                            long currentTime, long currentDuration) {
                        return value.ttlNanos();
                    }
                    @Override public long expireAfterRead(String key, LocalEntry value,
                                                          long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                });
        if (recordStats) builder.recordStats();
        return builder.build();
    }

    public record Stats(long localHits, long redisHits, long misses, long puts,
                        long oversizedEntries, long serializationFailures,
                        long redisFailures, long asynchronousDrops) {
    }

    private record LocalEntry(Object value, long ttlNanos) {
    }

    private record WriteTask(String key, byte[] value, Duration ttl, long epoch) {
    }

    private record VersionRead(boolean success, long value) {
    }

    private record PartitionRead(boolean success, boolean table,
                                 long tableVersion, long partitionVersion) {
        private static PartitionRead table(long tableVersion) {
            return new PartitionRead(true, true, tableVersion, 0L);
        }
        private static PartitionRead partition(long tableVersion, long partitionVersion) {
            return new PartitionRead(true, false, tableVersion, partitionVersion);
        }
        private static PartitionRead failure() {
            return new PartitionRead(false, false, 0L, 0L);
        }
    }

    private static final class LocalPartitionState {
        private final Map<String, Long> versions = new HashMap<>();
        private String field;
        private boolean table;
        private long tableVersion;

        private void toTable() {
            table = true;
            field = null;
            rotateTable();
        }

        private void rotateTable() {
            tableVersion++;
            versions.clear();
        }
    }

    @FunctionalInterface
    private interface Decoder {
        RedisValueCodec.Decoded decode(byte[] value) throws Exception;
    }
}

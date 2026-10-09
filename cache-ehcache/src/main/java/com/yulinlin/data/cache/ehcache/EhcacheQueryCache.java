package com.yulinlin.data.cache.ehcache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ehcache heap + persistent disk query cache. */
public final class EhcacheQueryCache implements QueryCache {

    private static final Logger log = LoggerFactory.getLogger(EhcacheQueryCache.class);
    private static final String CACHE_ALIAS = "yulinlin-query-cache";

    private final ObjectMapper objectMapper;
    private final PersistentCacheManager manager;
    private final Cache<String, byte[]> cache;

    public EhcacheQueryCache(QueryCacheProperties common,
                             EhcacheProperties properties,
                             ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.manager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(properties.getDirectory().toFile()))
                .withCache(CACHE_ALIAS, CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(String.class, byte[].class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(properties.getHeapEntries(), EntryUnit.ENTRIES)
                                        .disk(properties.getDiskSizeMb(), MemoryUnit.MB, true))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(common.getTtl())))
                .build(true);
        this.cache = manager.getCache(CACHE_ALIAS, String.class, byte[].class);
    }

    @Override
    public CacheLookup get(CacheKey key, CacheValueType valueType) {
        byte[] bytes = cache.get(key.value());
        if (bytes == null) return CacheLookup.miss();
        try {
            JavaType target = valueType.collection()
                    ? objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, valueType.valueClass())
                    : objectMapper.getTypeFactory().constructType(valueType.valueClass());
            return CacheLookup.hit(objectMapper.readValue(bytes, target));
        } catch (Exception error) {
            cache.remove(key.value());
            log.warn("Discarded unreadable query cache entry {}", key.value(), error);
            return CacheLookup.miss();
        }
    }

    @Override
    public void put(CacheKey key, CacheValueType valueType, Object value) {
        try {
            cache.put(key.value(), objectMapper.writeValueAsBytes(value));
        } catch (Exception error) {
            log.warn("Skipped query cache entry {} because it cannot be serialized", key.value(), error);
        }
    }

    @Override
    public void close() {
        manager.close();
    }
}

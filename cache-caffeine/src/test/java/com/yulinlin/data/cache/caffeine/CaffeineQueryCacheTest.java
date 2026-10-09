package com.yulinlin.data.cache.caffeine;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineQueryCacheTest {

    @Test
    void cachesNullAndExpiresAfterWrite() throws Exception {
        QueryCacheProperties common = new QueryCacheProperties();
        common.setTtl(Duration.ofMillis(40));
        CaffeineQueryCache cache = new CaffeineQueryCache(common, new CaffeineCacheProperties());
        CacheKey key = key();

        cache.put(key, CacheValueType.scalar(String.class), null);
        assertThat(cache.get(key, CacheValueType.scalar(String.class)).hit()).isTrue();

        Thread.sleep(100);
        assertThat(cache.get(key, CacheValueType.scalar(String.class)).hit()).isFalse();
    }

    @Test
    void explicitTtlOverridesTheSystemDefault() throws Exception {
        QueryCacheProperties common = new QueryCacheProperties();
        common.setTtl(Duration.ofSeconds(5));
        CaffeineQueryCache cache = new CaffeineQueryCache(common, new CaffeineCacheProperties());
        CacheKey logical = key();
        CacheKey defaultKey = cache.scope(logical, Set.of(), null);
        CacheKey key = cache.scope(logical, Set.of(), Duration.ofMillis(40));

        cache.put(defaultKey, CacheValueType.scalar(String.class), "default");
        assertThat(key).isNotEqualTo(defaultKey);
        assertThat(cache.get(key, CacheValueType.scalar(String.class)).hit()).isFalse();

        cache.put(key, CacheValueType.scalar(String.class), "value", Duration.ofMillis(40));
        assertThat(cache.get(key, CacheValueType.scalar(String.class)).hit()).isTrue();

        Thread.sleep(100);
        assertThat(cache.get(key, CacheValueType.scalar(String.class)).hit()).isFalse();
    }

    @Test
    void invalidatesOnlyTheSelectedNamespace() {
        QueryCacheProperties common = new QueryCacheProperties();
        CaffeineQueryCache cache = new CaffeineQueryCache(common, new CaffeineCacheProperties());
        CacheNamespace users = CacheNamespace.of(CaffeineQueryCacheTest.class, "master", "users");
        CacheNamespace roles = CacheNamespace.of(CaffeineQueryCacheTest.class, "master", "roles");
        CacheKey logical = key();
        CacheKey usersKey = cache.scope(logical, Set.of(users));
        CacheKey rolesKey = cache.scope(logical, Set.of(roles));
        CacheValueType type = CacheValueType.scalar(String.class);
        cache.put(usersKey, type, "users");
        cache.put(rolesKey, type, "roles");

        cache.invalidate(Set.of(users));

        assertThat(cache.get(cache.scope(logical, Set.of(users)), type).hit()).isFalse();
        assertThat(cache.get(cache.scope(logical, Set.of(roles)), type).value()).isEqualTo("roles");
    }

    @Test
    void anInFlightResultCannotRepopulateAnInvalidatedNamespace() {
        QueryCacheProperties common = new QueryCacheProperties();
        CaffeineQueryCache cache = new CaffeineQueryCache(common, new CaffeineCacheProperties());
        CacheNamespace users = CacheNamespace.of(CaffeineQueryCacheTest.class, "master", "users");
        CacheKey logical = key();
        CacheKey versionBeforeUpdate = cache.scope(logical, Set.of(users));

        cache.invalidate(Set.of(users));
        cache.put(versionBeforeUpdate, CacheValueType.scalar(String.class), "stale");

        CacheKey versionAfterUpdate = cache.scope(logical, Set.of(users));
        assertThat(cache.get(versionAfterUpdate, CacheValueType.scalar(String.class)).hit()).isFalse();
    }

    private static CacheKey key() {
        return CacheKey.query("mysql", "master", CaffeineQueryCacheTest.class,
                String.class, String.class, ParseType.select,
                new CommandNode<>("select #{id}", Map.of("id", 1), ParseType.select));
    }
}

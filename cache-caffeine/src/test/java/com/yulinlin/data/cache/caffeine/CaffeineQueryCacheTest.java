package com.yulinlin.data.cache.caffeine;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

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

    private static CacheKey key() {
        return CacheKey.query("mysql", "master", CaffeineQueryCacheTest.class,
                String.class, String.class, ParseType.select,
                new CommandNode<>("select #{id}", Map.of("id", 1), ParseType.select));
    }
}

package com.yulinlin.data.core.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryCachesTest {

    @Test
    void noProviderFallsBackToNoOpCache() {
        assertThat(QueryCaches.single(List.of())).isSameAs(NoOpQueryCache.INSTANCE);
    }

    @Test
    void oneProviderIsSelected() {
        QueryCache provider = new StubCache("only");

        assertThat(QueryCaches.single(List.of(provider))).isSameAs(provider);
    }

    @Test
    void multipleProvidersFailFast() {
        assertThatThrownBy(() -> QueryCaches.single(List.of(
                new StubCache("caffeine"), new StubCache("ehcache"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caffeine", "ehcache")
                .hasMessageContaining("Keep only cache-caffeine or cache-ehcache");
    }

    private record StubCache(String providerName) implements QueryCache {
        @Override
        public CacheLookup get(CacheKey key, CacheValueType valueType) {
            return CacheLookup.miss();
        }

        @Override
        public void put(CacheKey key, CacheValueType valueType, Object value) {
        }
    }
}

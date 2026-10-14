package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.AbstractSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QueryCachePartitionContractTest {

    @Test
    void defaultPartitionScopeFallsBackToItsTableNamespace() {
        RecordingCache cache = new RecordingCache();
        CacheNamespace namespace = CacheNamespace.of(AbstractSession.class, "mysql", "users");
        CachePartition partition = CachePartition.of(namespace, "username", "alice").orElseThrow();
        CacheKey logical = logicalKey();

        CacheKey scoped = cache.scopePartition(logical, partition, Duration.ofSeconds(20));

        assertThat(scoped).isSameAs(cache.scopedResult);
        assertThat(cache.namespaces).containsExactly(namespace);
        assertThat(cache.ttl).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void partitionPhysicalKeyIncludesEveryVersionAndOptionalTtl() {
        CacheKey logical = logicalKey();
        CacheKey base = logical.scopedPartition(1, 2, 3, Duration.ofSeconds(10));

        assertThat(base)
                .isEqualTo(logical.scopedPartition(1, 2, 3, Duration.ofSeconds(10)))
                .isNotEqualTo(logical.scopedPartition(2, 2, 3, Duration.ofSeconds(10)))
                .isNotEqualTo(logical.scopedPartition(1, 3, 3, Duration.ofSeconds(10)))
                .isNotEqualTo(logical.scopedPartition(1, 2, 4, Duration.ofSeconds(10)))
                .isNotEqualTo(logical.scopedPartition(1, 2, 3, Duration.ofSeconds(11)));
    }

    private static CacheKey logicalKey() {
        return CacheKey.query("mysql", "master", AbstractSession.class, Object.class, Object.class,
                ParseType.select, new CommandNode<>("select 1", Map.of(), ParseType.select));
    }

    private static final class RecordingCache implements QueryCache {
        private final CacheKey scopedResult = CacheKey.application("test", "scoped", 0, 0);
        private Set<CacheNamespace> namespaces;
        private Duration ttl;

        @Override
        public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
            this.namespaces = namespaces;
            this.ttl = ttl;
            return scopedResult;
        }

        @Override public CacheLookup get(CacheKey key, CacheValueType valueType) { return CacheLookup.miss(); }
        @Override public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) { }
    }
}

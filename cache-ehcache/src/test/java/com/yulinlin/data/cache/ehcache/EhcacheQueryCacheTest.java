package com.yulinlin.data.cache.ehcache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EhcacheQueryCacheTest {

    // Ehcache uses a memory-mapped disk store that Windows may keep mapped until JVM exit.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path directory;

    @Test
    void survivesCleanCacheManagerRestart() {
        QueryCacheProperties common = new QueryCacheProperties();
        EhcacheProperties properties = new EhcacheProperties();
        properties.setDirectory(directory);
        properties.setHeapEntries(10);
        properties.setDiskSizeMb(10);
        CacheKey key = key();
        CacheValueType type = CacheValueType.listOf(UserRow.class);
        CacheNamespace namespace = CacheNamespace.of(EhcacheQueryCacheTest.class, "master", "users");

        try (EhcacheQueryCache first = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            first.put(first.scope(key, Set.of(namespace)), type, List.of(new UserRow(7, "admin")));
        }

        try (EhcacheQueryCache second = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            CacheLookup lookup = second.get(second.scope(key, Set.of(namespace)), type);
            assertThat(lookup.hit()).isTrue();
            assertThat((List<UserRow>) lookup.value()).containsExactly(new UserRow(7, "admin"));
        }
    }

    @Test
    void namespaceInvalidationSurvivesRestart() {
        QueryCacheProperties common = new QueryCacheProperties();
        EhcacheProperties properties = new EhcacheProperties();
        properties.setDirectory(directory);
        properties.setHeapEntries(10);
        properties.setDiskSizeMb(10);
        CacheKey logical = key();
        CacheValueType type = CacheValueType.scalar(UserRow.class);
        CacheNamespace namespace = CacheNamespace.of(EhcacheQueryCacheTest.class, "master", "users");

        try (EhcacheQueryCache first = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            first.put(first.scope(logical, Set.of(namespace)), type, new UserRow(7, "admin"));
            first.invalidate(Set.of(namespace));
        }

        try (EhcacheQueryCache second = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            CacheLookup lookup = second.get(second.scope(logical, Set.of(namespace)), type);
            assertThat(lookup.hit()).isFalse();
        }
    }

    private static CacheKey key() {
        return CacheKey.query("mysql", "master", EhcacheQueryCacheTest.class,
                UserRow.class, UserRow.class, ParseType.select,
                new CommandNode<>("select * from user where id=#{id}", Map.of("id", 7), ParseType.select));
    }

    record UserRow(long id, String name) {
    }
}

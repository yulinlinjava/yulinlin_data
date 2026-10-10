package com.yulinlin.data.cache.ehcache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.CacheClient;
import com.yulinlin.data.core.cache.DefaultCacheClient;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Duration;
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

    @Test
    void ttlParticipationInKeyFollowsTheCommonSetting() {
        QueryCacheProperties common = new QueryCacheProperties();
        CacheKey logical = key();

        try (EhcacheQueryCache cache = new EhcacheQueryCache(common, properties(), new ObjectMapper())) {
            CacheKey shortLived = cache.scope(logical, Set.of(), Duration.ofSeconds(10));
            CacheKey longLived = cache.scope(logical, Set.of(), Duration.ofMinutes(10));
            assertThat(shortLived).isEqualTo(longLived);

            common.setTtlInKey(true);
            shortLived = cache.scope(logical, Set.of(), Duration.ofSeconds(10));
            longLived = cache.scope(logical, Set.of(), Duration.ofMinutes(10));
            assertThat(shortLived).isNotEqualTo(longLived);
        }
    }

    @Test
    void publicCacheApiAndItsNamespaceVersionsSurviveRestart() {
        QueryCacheProperties common = new QueryCacheProperties();
        EhcacheProperties properties = new EhcacheProperties();
        properties.setDirectory(directory);
        properties.setHeapEntries(10);
        properties.setDiskSizeMb(10);

        try (EhcacheQueryCache first = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            CacheClient client = new DefaultCacheClient(first);
            client.set("user", "7", new UserRow(7, "admin"));
            client.set("user-list", "enabled", List.of(new UserRow(7, "admin")));
        }

        try (EhcacheQueryCache second = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            CacheClient client = new DefaultCacheClient(second);
            assertThat(client.get("user", "7", UserRow.class)).isEqualTo(new UserRow(7, "admin"));
            assertThat(client.get("user-list", "enabled", new TypeReference<List<UserRow>>() { }))
                    .containsExactly(new UserRow(7, "admin"));
            client.invalidate("user");
        }

        try (EhcacheQueryCache third = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            CacheClient client = new DefaultCacheClient(third);
            assertThat(client.get("user", "7", UserRow.class)).isNull();
        }
    }

    @Test
    void bindsPersonalizedEhcacheSettings() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EhcacheAutoConfiguration.class))
                .withPropertyValues(
                        "yulinlin.cache.ehcache.directory=" + directory.toString().replace('\\', '/'),
                        "yulinlin.cache.ehcache.heap-entries=32",
                        "yulinlin.cache.ehcache.offheap-size-mb=1",
                        "yulinlin.cache.ehcache.disk-size-mb=10",
                        "yulinlin.cache.ehcache.persistent=false",
                        "yulinlin.cache.ehcache.expiration-policy=after-access",
                        "yulinlin.cache.ehcache.disk-threads=1",
                        "yulinlin.cache.ehcache.maximum-entry-size-mb=2",
                        "yulinlin.cache.ehcache.record-statistics=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(EhcacheQueryCache.class);
                    EhcacheProperties properties = context.getBean(EhcacheProperties.class);
                    assertThat(properties.getHeapEntries()).isEqualTo(32);
                    assertThat(properties.getOffheapSizeMb()).isEqualTo(1);
                    assertThat(properties.getDiskSizeMb()).isEqualTo(10);
                    assertThat(properties.isPersistent()).isFalse();
                    assertThat(properties.getExpirationPolicy())
                            .isEqualTo(EhcacheProperties.ExpirationPolicy.AFTER_ACCESS);
                    assertThat(properties.getDiskThreads()).isEqualTo(1);
                    assertThat(properties.getMaximumEntrySizeMb()).isEqualTo(2);
                    assertThat(properties.isRecordStatistics()).isFalse();
                });
    }

    @Test
    void afterAccessRenewsTheEntryTtl() throws Exception {
        QueryCacheProperties common = new QueryCacheProperties();
        common.setTtl(Duration.ofMillis(400));
        EhcacheProperties properties = properties();
        properties.setExpirationPolicy(EhcacheProperties.ExpirationPolicy.AFTER_ACCESS);

        try (EhcacheQueryCache cache = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            CacheKey key = key();
            CacheValueType type = CacheValueType.scalar(UserRow.class);
            cache.put(key, type, new UserRow(7, "admin"));

            Thread.sleep(250);
            assertThat(cache.get(key, type).hit()).isTrue();
            Thread.sleep(250);
            assertThat(cache.get(key, type).hit()).isTrue();
            Thread.sleep(450);
            assertThat(cache.get(key, type).hit()).isFalse();
        }
    }

    @Test
    void skipsOversizedEntriesAndRecordsStatistics() {
        QueryCacheProperties common = new QueryCacheProperties();
        EhcacheProperties properties = properties();
        properties.setMaximumEntrySizeMb(1);

        try (EhcacheQueryCache cache = new EhcacheQueryCache(common, properties, new ObjectMapper())) {
            String oversized = "x".repeat(1024 * 1024);
            cache.putApplication("blob", "large", oversized, null);

            assertThat(cache.getApplication("blob", "large", String.class).hit()).isFalse();
            assertThat(cache.stats()).isEqualTo(new EhcacheQueryCache.Stats(0, 1, 0, 1, 0));
            cache.resetStatistics();
            assertThat(cache.stats()).isEqualTo(new EhcacheQueryCache.Stats(0, 0, 0, 0, 0));
        }
    }

    private EhcacheProperties properties() {
        EhcacheProperties properties = new EhcacheProperties();
        properties.setDirectory(directory);
        properties.setHeapEntries(10);
        properties.setDiskSizeMb(10);
        properties.setDiskThreads(1);
        return properties;
    }

    private static CacheKey key() {
        return CacheKey.query("mysql", "master", EhcacheQueryCacheTest.class,
                UserRow.class, UserRow.class, ParseType.select,
                new CommandNode<>("select * from user where id=#{id}", Map.of("id", 7), ParseType.select));
    }

    record UserRow(long id, String name) {
    }
}

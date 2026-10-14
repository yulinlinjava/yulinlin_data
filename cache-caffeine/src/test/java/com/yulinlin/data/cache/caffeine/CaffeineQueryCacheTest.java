package com.yulinlin.data.cache.caffeine;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.CacheClient;
import com.yulinlin.data.core.cache.DefaultCacheClient;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineQueryCacheTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CaffeineCacheAutoConfiguration.class));

    @Test
    void statisticsAreDisabledByDefault() {
        CaffeineCacheProperties properties = new CaffeineCacheProperties();
        assertThat(properties.isRecordStats()).isFalse();

        CaffeineQueryCache cache = new CaffeineQueryCache(new QueryCacheProperties(), properties);
        cache.get(key(), CacheValueType.scalar(String.class));
        assertThat(cache.stats().requestCount()).isZero();
    }

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
        common.setTtlInKey(true);
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
    void differentTtlsShareTheSameKeyByDefault() {
        QueryCacheProperties common = new QueryCacheProperties();
        CaffeineQueryCache cache = new CaffeineQueryCache(common, new CaffeineCacheProperties());
        CacheKey logical = key();

        CacheKey shortLived = cache.scope(logical, Set.of(), Duration.ofSeconds(10));
        CacheKey longLived = cache.scope(logical, Set.of(), Duration.ofMinutes(10));

        assertThat(common.isTtlInKey()).isFalse();
        assertThat(shortLived).isEqualTo(longLived);
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

    @Test
    void invalidatesOnlyOneValueOfALearnedEqualityField() {
        CaffeineQueryCache cache = new CaffeineQueryCache(
                new QueryCacheProperties(), new CaffeineCacheProperties());
        CacheNamespace users = CacheNamespace.of(CaffeineQueryCacheTest.class, "mysql", "users");
        CachePartition alice = partition(users, "username", "alice");
        CachePartition bob = partition(users, "username", "bob");
        CacheValueType type = CacheValueType.scalar(String.class);
        CacheKey aliceLogical = key("username", "alice");
        CacheKey bobLogical = key("username", "bob");

        cache.put(cache.scopePartition(aliceLogical, alice), type, "Alice");
        cache.put(cache.scopePartition(bobLogical, bob), type, "Bob");

        // A write still expires ordinary list/count caches, but only Alice's equality bucket.
        cache.invalidate(Set.of(users));
        cache.invalidatePartitions(Set.of(alice));

        assertThat(cache.get(cache.scopePartition(aliceLogical, alice), type).hit()).isFalse();
        assertThat(cache.get(cache.scopePartition(bobLogical, bob), type).value()).isEqualTo("Bob");
    }

    @Test
    void secondEqualityFieldPermanentlyFallsBackToTableScoping() {
        CaffeineQueryCache cache = new CaffeineQueryCache(
                new QueryCacheProperties(), new CaffeineCacheProperties());
        CacheNamespace users = CacheNamespace.of(CaffeineQueryCacheTest.class, "mysql", "users");
        CachePartition username = partition(users, "username", "alice");
        CachePartition email = partition(users, "email", "alice@example.com");
        CacheValueType type = CacheValueType.scalar(String.class);
        CacheKey usernameLogical = key("username", "alice");
        CacheKey firstUsernameKey = cache.scopePartition(usernameLogical, username);
        cache.put(firstUsernameKey, type, "partitioned");

        cache.scopePartition(key("email", "alice@example.com"), email);
        CacheKey tableScopedUsername = cache.scopePartition(usernameLogical, username);

        assertThat(tableScopedUsername).isNotEqualTo(firstUsernameKey);
        assertThat(cache.get(tableScopedUsername, type).hit()).isFalse();
        cache.put(tableScopedUsername, type, "table");
        cache.invalidate(Set.of(users));
        assertThat(cache.get(cache.scopePartition(usernameLogical, username), type).hit()).isFalse();
    }

    @Test
    void writesDoNotLearnAFieldButAMismatchedWriteUpgradesALearnedField() {
        CaffeineQueryCache cache = new CaffeineQueryCache(
                new QueryCacheProperties(), new CaffeineCacheProperties());
        CacheNamespace users = CacheNamespace.of(CaffeineQueryCacheTest.class, "mysql", "users");
        CachePartition username = partition(users, "username", "alice");
        CachePartition email = partition(users, "email", "alice@example.com");
        CacheValueType type = CacheValueType.scalar(String.class);
        CacheKey usernameLogical = key("username", "alice");

        cache.invalidatePartitions(Set.of(email));
        CacheKey learnedUsernameKey = cache.scopePartition(usernameLogical, username);
        cache.put(learnedUsernameKey, type, "Alice");

        cache.invalidatePartitions(Set.of(email));

        CacheKey tableScopedUsername = cache.scopePartition(usernameLogical, username);
        assertThat(tableScopedUsername).isNotEqualTo(learnedUsernameKey);
        assertThat(cache.get(tableScopedUsername, type).hit()).isFalse();
    }

    @Test
    void tablePartitionInvalidationKeepsTheLearnedSingleField() {
        CaffeineQueryCache cache = new CaffeineQueryCache(
                new QueryCacheProperties(), new CaffeineCacheProperties());
        CacheNamespace users = CacheNamespace.of(CaffeineQueryCacheTest.class, "mysql", "users");
        CachePartition alice = partition(users, "username", "alice");
        CachePartition bob = partition(users, "username", "bob");
        CacheValueType type = CacheValueType.scalar(String.class);
        CacheKey aliceLogical = key("username", "alice");
        CacheKey bobLogical = key("username", "bob");
        cache.put(cache.scopePartition(aliceLogical, alice), type, "Alice");
        cache.put(cache.scopePartition(bobLogical, bob), type, "Bob");

        cache.invalidatePartitionTables(Set.of(users));

        assertThat(cache.get(cache.scopePartition(aliceLogical, alice), type).hit()).isFalse();
        assertThat(cache.get(cache.scopePartition(bobLogical, bob), type).hit()).isFalse();

        cache.put(cache.scopePartition(aliceLogical, alice), type, "Alice2");
        cache.put(cache.scopePartition(bobLogical, bob), type, "Bob2");
        cache.invalidatePartitions(Set.of(alice));
        assertThat(cache.get(cache.scopePartition(aliceLogical, alice), type).hit()).isFalse();
        assertThat(cache.get(cache.scopePartition(bobLogical, bob), type).value()).isEqualTo("Bob2");
    }

    @Test
    void excessiveDistinctValuesRotateTheTablePartitionVersion() {
        CaffeineQueryCache cache = new CaffeineQueryCache(
                new QueryCacheProperties(), new CaffeineCacheProperties());
        CacheNamespace users = CacheNamespace.of(CaffeineQueryCacheTest.class, "mysql", "users");
        CacheValueType type = CacheValueType.scalar(String.class);
        CachePartition first = partition(users, "username", "user-0");
        CacheKey firstLogical = key("username", "user-0");
        cache.put(cache.scopePartition(firstLogical, first), type, "first");

        for (int index = 1; index <= 4096; index++) {
            CachePartition next = partition(users, "username", "user-" + index);
            cache.scopePartition(key("username", "user-" + index), next);
        }

        assertThat(cache.get(cache.scopePartition(firstLogical, first), type).hit()).isFalse();
    }

    @Test
    void publicCacheApiIsIndependentFromQueryInvalidation() {
        QueryCacheProperties common = new QueryCacheProperties();
        CaffeineQueryCache provider = new CaffeineQueryCache(common, new CaffeineCacheProperties());
        CacheClient client = new DefaultCacheClient(provider);

        client.set("user", "1", "admin");
        provider.invalidateAll();
        assertThat(client.get("user", "1", String.class)).isEqualTo("admin");
        assertThat(client.exists("user", "1")).isTrue();

        client.invalidate("user");
        assertThat(client.get("user", "1", String.class)).isNull();
        client.set("user", "1", "new");
        assertThat(client.remove("user", "1")).isTrue();
        assertThat(client.remove("user", "1")).isFalse();
    }

    @Test
    void bindsPersonalizedCaffeineSettings() {
        contextRunner.withPropertyValues(
                        "yulinlin.cache.caffeine.initial-capacity=64",
                        "yulinlin.cache.caffeine.maximum-size=256",
                        "yulinlin.cache.caffeine.expiration-policy=after-access",
                        "yulinlin.cache.caffeine.use-system-scheduler=true",
                        "yulinlin.cache.caffeine.executor=direct",
                        "yulinlin.cache.caffeine.record-stats=false",
                        "yulinlin.cache.ttl-in-key=true",
                        "yulinlin.cache.clear-on-write=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CaffeineQueryCache.class);
                    CaffeineCacheProperties properties = context.getBean(CaffeineCacheProperties.class);
                    assertThat(properties.getInitialCapacity()).isEqualTo(64);
                    assertThat(properties.getMaximumSize()).isEqualTo(256);
                    assertThat(properties.getExpirationPolicy())
                            .isEqualTo(CaffeineCacheProperties.ExpirationPolicy.AFTER_ACCESS);
                    assertThat(properties.isUseSystemScheduler()).isTrue();
                    assertThat(properties.getExecutor())
                            .isEqualTo(CaffeineCacheProperties.ExecutorMode.DIRECT);
                    assertThat(properties.isRecordStats()).isFalse();
                    QueryCacheProperties common = context.getBean(QueryCacheProperties.class);
                    assertThat(common.isTtlInKey()).isTrue();
                    assertThat(common.isClearOnWrite()).isFalse();
                    assertThat(context.getBean(CaffeineQueryCache.class).clearOnWrite()).isFalse();
                });
    }

    @Test
    void afterAccessRenewsTheEntryTtl() throws Exception {
        QueryCacheProperties common = new QueryCacheProperties();
        common.setTtl(Duration.ofMillis(150));
        CaffeineCacheProperties properties = new CaffeineCacheProperties();
        properties.setExpirationPolicy(CaffeineCacheProperties.ExpirationPolicy.AFTER_ACCESS);
        properties.setExecutor(CaffeineCacheProperties.ExecutorMode.DIRECT);
        CaffeineQueryCache cache = new CaffeineQueryCache(common, properties);
        CacheKey key = key();
        CacheValueType type = CacheValueType.scalar(String.class);
        cache.put(key, type, "value");

        Thread.sleep(100);
        assertThat(cache.get(key, type).hit()).isTrue();
        Thread.sleep(100);
        assertThat(cache.get(key, type).hit()).isTrue();
        Thread.sleep(180);
        cache.cleanUp();
        assertThat(cache.get(key, type).hit()).isFalse();
    }

    @Test
    void maximumSizeIsSharedByQueryAndApplicationEntries() {
        QueryCacheProperties common = new QueryCacheProperties();
        CaffeineCacheProperties properties = new CaffeineCacheProperties();
        properties.setMaximumSize(2);
        properties.setExecutor(CaffeineCacheProperties.ExecutorMode.DIRECT);
        CaffeineQueryCache cache = new CaffeineQueryCache(common, properties);

        cache.putApplication("users", "1", "one", null);
        cache.putApplication("users", "2", "two", null);
        cache.putApplication("users", "3", "three", null);
        cache.cleanUp();

        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
    }

    private static CacheKey key() {
        return CacheKey.query("mysql", "master", CaffeineQueryCacheTest.class,
                String.class, String.class, ParseType.select,
                new CommandNode<>("select #{id}", Map.of("id", 1), ParseType.select));
    }

    private static CacheKey key(String field, Object value) {
        return CacheKey.query("mysql", "master", CaffeineQueryCacheTest.class,
                String.class, String.class, ParseType.select,
                new CommandNode<>("select * from users where " + field + " = #{value}",
                        Map.of("value", value), ParseType.select));
    }

    private static CachePartition partition(CacheNamespace namespace, String field, Object value) {
        return CachePartition.of(namespace, field, value).orElseThrow();
    }
}

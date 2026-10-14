package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteSessionCacheInvalidationTest {

    @Test
    void clearOnWriteIsEnabledByDefault() {
        QueryCacheProperties properties = new QueryCacheProperties();

        assertThat(properties.isClearOnWrite()).isTrue();
        properties.setClearOnWrite(false);
        assertThat(properties.isClearOnWrite()).isFalse();
    }

    @Test
    void invalidatesAnnotatedTableAfterSuccessfulWrite() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class);
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.namespaces).extracting(CacheNamespace::resource).containsExactly("users");
    }

    @Test
    void invalidatesOnlyTheMatchingEqualityPartition() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.table("users").fields(fields -> fields.field("name", "new"));
        wrapper.where().eq("username", "Alice");
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class).addRequest(wrapper);
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.namespaces).extracting(CacheNamespace::resource).containsExactly("users");
        assertThat(cache.partitions).hasSize(1);
        assertThat(cache.partitions.iterator().next().field()).isEqualTo("username");
        assertThat(cache.partitionTables).isEmpty();
    }

    @Test
    void changingTheConditionFieldFallsBackToAllTablePartitions() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.table("users").fields(fields -> fields.field("username", "Bob"));
        wrapper.where().eq("username", "Alice");
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class).addRequest(wrapper);
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.partitions).isEmpty();
        assertThat(cache.partitionTables).extracting(CacheNamespace::resource).containsExactly("users");
    }

    @Test
    void routeTransactionDefersInvalidationUntilCommitAndDiscardsItOnRollback() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class);
        doReturn(1).when(node).update(request);

        route.startTransaction();
        route.update(request);
        assertThat(cache.namespaces).isEmpty();
        route.rollbackTransaction();
        assertThat(cache.namespaces).isEmpty();

        route.startTransaction();
        route.update(request);
        assertThat(cache.namespaces).isEmpty();
        route.commitTransaction();
        assertThat(cache.namespaces).extracting(CacheNamespace::resource).containsExactly("users");
    }

    @Test
    void rawSqlFallsBackToGlobalInvalidationWhenNoNamespaceIsProvided() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<Object> request = ExecuteRequest
                .newInstance("update users set name=#{name}", java.util.Map.of("name", "new"));
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);
        assertThat(cache.allInvalidations).isEqualTo(1);
    }

    @Test
    void globalClearCacheCanBeDisabled() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        route.setClearOnWrite(false);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class);
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.namespaces).isEmpty();
        assertThat(cache.allInvalidations).isZero();
    }

    @Test
    void requestClearCacheFalseOverridesEnabledGlobalDefault() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class).clearCache(false);
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.namespaces).isEmpty();
        assertThat(cache.allInvalidations).isZero();
    }

    @Test
    void requestClearCacheTrueOverridesDisabledGlobalDefault() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        route.setClearOnWrite(false);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class).clearCache(true);
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.namespaces).extracting(CacheNamespace::resource).containsExactly("users");
        assertThat(cache.allInvalidations).isZero();
    }

    @Test
    void unavailableProviderSkipsInvalidationEvenWhenRequestForcesIt() {
        RecordingCache cache = new RecordingCache(false);
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class).clearCache(true);
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.namespaces).isEmpty();
        assertThat(cache.allInvalidations).isZero();
    }

    private static EntitySession node() {
        EntitySession node = mock(EntitySession.class);
        when(node.group()).thenReturn("mysql");
        when(node.cluster()).thenReturn(JoinCluster.master);
        when(node.weight()).thenReturn(1);
        return node;
    }

    private static RouteSession route(EntitySession node, QueryCache cache) {
        RouteSession route = RouteSession.builder().filterManager(new IFilterManager() { }).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.setQueryCache(cache);
        route.registerSession(node);
        return route;
    }

    @JoinTable("users")
    private static final class User {
    }

    private static final class RecordingCache implements QueryCache {
        final Set<CacheNamespace> namespaces = new LinkedHashSet<>();
        final Set<CachePartition> partitions = new LinkedHashSet<>();
        final Set<CacheNamespace> partitionTables = new LinkedHashSet<>();
        final boolean available;
        int allInvalidations;
        RecordingCache() { this(true); }
        RecordingCache(boolean available) { this.available = available; }
        @Override public boolean available() { return available; }
        @Override public CacheLookup get(CacheKey key, CacheValueType valueType) { return CacheLookup.miss(); }
        @Override public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) { }
        @Override public void invalidate(Set<CacheNamespace> values) { namespaces.addAll(values); }
        @Override public void invalidatePartitions(Set<CachePartition> values) { partitions.addAll(values); }
        @Override public void invalidatePartitionTables(Set<CacheNamespace> values) {
            partitionTables.addAll(values);
        }
        @Override public void invalidateAll() {
            allInvalidations++;
            namespaces.clear();
            partitions.clear();
            partitionTables.clear();
        }
    }
}

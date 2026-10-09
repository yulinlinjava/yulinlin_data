package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.request.ExecuteRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteSessionCacheInvalidationTest {

    @Test
    void invalidatesAnnotatedTableAfterSuccessfulWrite() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class).invalidate();
        doReturn(1).when(node).update(request);

        assertThat(route.update(request)).isEqualTo(1);

        assertThat(cache.namespaces).extracting(CacheNamespace::resource).containsExactly("users");
    }

    @Test
    void routeTransactionDefersInvalidationUntilCommitAndDiscardsItOnRollback() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class).invalidate();
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
    void rawSqlRequiresAnExplicitNamespace() {
        RecordingCache cache = new RecordingCache();
        EntitySession node = node();
        RouteSession route = route(node, cache);
        ExecuteRequest<Object> request = ExecuteRequest
                .newInstance("update users set name=#{name}", java.util.Map.of("name", "new"))
                .invalidate();

        assertThatThrownBy(() -> route.update(request))
                .isInstanceOf(NoticeException.class)
                .hasMessageContaining("invalidate(\"表名\")");
        verify(node, never()).update(request);
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
        @Override public CacheLookup get(CacheKey key, CacheValueType valueType) { return CacheLookup.miss(); }
        @Override public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) { }
        @Override public void invalidate(Set<CacheNamespace> values) { namespaces.addAll(values); }
        @Override public void invalidateAll() { namespaces.clear(); }
    }
}

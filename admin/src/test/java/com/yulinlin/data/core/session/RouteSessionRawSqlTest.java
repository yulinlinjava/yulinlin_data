package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinSession;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteSessionRawSqlTest {
    private EntitySession node() {
        var node = mock(EntitySession.class);
        when(node.group()).thenReturn("local");
        when(node.cluster()).thenReturn(JoinCluster.master);
        when(node.weight()).thenReturn(1);
        return node;
    }

    private RouteSession route(EntitySession node) {
        var route = RouteSession.builder().filterManager(new IFilterManager() { }).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.registerSession(node);
        return route;
    }

    @Test void missingAndObjectSourcesUseDefaultGroupAndDelegateNormally() {
        for (Class<?> source : new Class<?>[]{null, Object.class}) {
            var node = node();
            var route = route(node);
            var query = QueryRequest.newInstance("select 1 as value", Map.of(), Map.class);
            query.setFromClass(source);
            var rows = List.of(Map.of("value", 1));
            doReturn(rows).when(node).select(query);
            doReturn(rows).when(node).group(query);
            doReturn(1).when(node).count(query);
            assertThat(route.select(query)).isSameAs(rows);
            assertThat(route.group(query)).isSameAs(rows);
            assertThat(route.count(query)).isEqualTo(1);
            assertThat(query.getSession()).isEqualTo("local");
            assertThat(query.getFromClass()).isSameAs(source);

            var write = ExecuteRequest.newInstance("delete from entries where id=#{id}", Map.of("id", 1));
            write.setFromClass(source);
            doReturn(1).when(node).update(write);
            assertThat(route.update(write)).isEqualTo(1);
            assertThat(write.getFromClass()).isSameAs(source);
            verify(node).update(write);
        }
    }

    @Test void recursiveRawQueryFailsWithDepthMessageNotNullPointerAndCleansUp() {
        int previousLimit = RouteSession.deep;
        RouteSession.deep = 2;
        try {
            var node = node();
            var route = route(node);
            var query = QueryRequest.newInstance("select 1 as value", Map.of(), Map.class);
            query.setFromClass(null);
            doAnswer(call -> route.select(query)).when(node).select(query);
            assertThatThrownBy(() -> route.select(query)).isInstanceOf(NoticeException.class)
                    .hasMessageContaining("自定义SQL");
            var rows = List.of(Map.of("value", 1));
            doReturn(rows).when(node).select(query);
            assertThat(route.select(query)).isSameAs(rows);
        } finally { RouteSession.deep = previousLimit; }
    }

    @Test void annotationRouteOutranksEntityRouteAndIsRestored() {
        EntitySession methodNode = node("method");
        EntitySession entityNode = node("entity");
        var route = RouteSession.builder().filterManager(new IFilterManager() { }).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.registerSession(List.of(methodNode, entityNode));
        var query = QueryRequest.newInstance("select 1 as value", Map.of(), Map.class);
        query.setFromClass(EntityRouted.class);
        var rows = List.of(Map.of("value", 1));
        doReturn(rows).when(methodNode).select(query);

        route.pushAnnotatedSession("method", JoinCluster.master);
        try {
            assertThat(route.select(query)).isSameAs(rows);
        } finally {
            route.popAnnotatedSession();
        }

        assertThat(query.getSession()).isEqualTo("method");
        verify(methodNode).select(query);
        verify(entityNode, never()).select(query);
    }

    private EntitySession node(String group) {
        var node = mock(EntitySession.class);
        when(node.group()).thenReturn(group);
        when(node.cluster()).thenReturn(JoinCluster.master);
        when(node.weight()).thenReturn(1);
        when(node.ping()).thenReturn(true);
        return node;
    }

    @JoinSession("entity")
    static class EntityRouted {
    }
}

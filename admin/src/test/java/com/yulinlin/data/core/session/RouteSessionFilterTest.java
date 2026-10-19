package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.cache.*;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.filter.*;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.queue.WriteQueueManager;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 过滤器只经路由入口执行；SQL/缓存用真实解析器，数据库执行使用内存桩。 */
@SuppressWarnings({"rawtypes", "unchecked"})
class RouteSessionFilterTest {
    @AfterEach void clean() {
        while (TransactionListenerManager.currentContext() != null)
            TransactionListenerManager.currentContext().manager().rollbackTransaction();
    }

    private EntitySession node(String group, JoinCluster cluster) {
        EntitySession node = mock(EntitySession.class);
        when(node.group()).thenReturn(group);
        when(node.cluster()).thenReturn(cluster);
        when(node.weight()).thenReturn(1);
        return node;
    }

    private RouteSession route(IFilter filter, EntitySession... nodes) {
        RouteSession route = RouteSession.builder()
                .filterManager(new SimpFilterManager(List.of(filter))).build();
        RandomLoadBalance balance = new RandomLoadBalance();
        balance.setDefaultGroup(nodes[0].group());
        route.setLoadBalance(balance);
        route.registerSession(List.of(nodes));
        return route;
    }

    private QueryRequest<Map> query() {
        QueryRequest<Map> request = QueryRequest.newInstance(Map.class, select("users"));
        request.setFromClass(User.class);
        return request;
    }

    private SelectWrapper<Map> select(String table) {
        SelectWrapper<Map> wrapper = new SelectWrapper<Map>().table(table);
        wrapper.fields().field("id", "id");
        return wrapper;
    }

    @Test void replacementQueryRewritesTableAndSwitchesSourceWithoutLosingOptions() {
        EntitySession mysql = node("mysql", JoinCluster.master);
        EntitySession archive = node("archive", JoinCluster.master);
        AtomicReference<QueryRequest<?>> replacement = new AtomicReference<>();
        IFilter filter = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                QueryRequest<Map> next = QueryRequest.newInstance(Map.class, select("users_202610"));
                next.setFromClass(request.getFromClass());
                next.setSession("archive");
                replacement.set(next);
                return next;
            }
            @Override public Object after(String group, BaseRequest request, Object data) {
                assertThat(group).isEqualTo("archive");
                assertThat(request).isSameAs(replacement.get());
                return List.of(Map.of("id", "enhanced"));
            }
        };
        RouteSession route = route(filter, mysql, archive);
        QueryRequest<Map> request = query().cache(Duration.ofSeconds(20)).autoUpdate(false);
        request.setRoot(Map.of("tenant", 1));
        doReturn(List.of(Map.of("id", "raw"))).when(archive).select(any());

        assertThat(route.select(request)).containsExactly(Map.of("id", "enhanced"));
        assertThat(replacement.get().getCacheMode()).isEqualTo(CacheMode.READ_THROUGH);
        assertThat(replacement.get().getCacheTtl()).isEqualTo(Duration.ofSeconds(20));
        assertThat(replacement.get().getRoot()).isSameAs(request.getRoot());
        assertThat(replacement.get().getAutoUpdate()).isFalse();
        assertThat(replacement.get().getFromClass()).isEqualTo(User.class);
        verify(mysql, never()).select(any());
        assertThat(route.session()).isSameAs(mysql);
    }

    @Test void sameGroupClusterSwitchAndRepeatedRequestsRestoreOuterRoute() {
        EntitySession master = node("mysql", JoinCluster.master);
        EntitySession replica = node("mysql", JoinCluster.slave);
        IFilter filter = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                request.setCluster(JoinCluster.slave);
                return request;
            }
        };
        RouteSession route = route(filter, master, replica);
        EntitySession outer = node("outer", JoinCluster.master);
        QueryRequest<Map> request = query();
        request.setSession("mysql");
        doReturn(List.of()).when(replica).select(any());
        route.pushSession(outer);
        try {
            route.select(request);
            route.select(request);
            assertThat(route.session()).isSameAs(outer);
        } finally { route.popSession(); }
        assertThat(route.session()).isSameAs(master);
        verify(replica, times(2)).select(request);
    }

    @Test void failedPreparationAndWrongResultTypeDoNotLeakRouteEntries() {
        EntitySession mysql = node("mysql", JoinCluster.master);
        AtomicInteger mode = new AtomicInteger();
        IFilter filter = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                return switch (mode.get()) {
                    case 0 -> null;
                    case 1 -> ExecuteRequest.ofUpdate(User.class);
                    default -> request;
                };
            }
            @Override public Object after(String group, BaseRequest request, Object data) {
                return mode.get() == 2 ? "not a List" : data;
            }
        };
        RouteSession route = route(filter, mysql);
        EntitySession outer = node("outer", JoinCluster.master);
        doReturn(List.of()).when(mysql).select(any());
        route.pushSession(outer);
        try {
            for (int value = 0; value < 3; value++) {
                mode.set(value);
                QueryRequest<Map> request = query(); request.setSession("mysql");
                assertThatThrownBy(() -> route.select(request)).isInstanceOf(RuntimeException.class);
                assertThat(route.session()).isSameAs(outer);
            }
        } finally { route.popSession(); }
        assertThat(route.session()).isSameAs(mysql);
    }

    @Test void pageEnhancementRunsOnceAndKeepsTotalAndExtraWhenReplacingItsList() {
        EntitySession mysql = node("mysql", JoinCluster.master);
        AtomicInteger before = new AtomicInteger(); AtomicInteger after = new AtomicInteger();
        IRequestFilter filter = new IRequestFilter() {
            @Override public QueryRequest selectBefore(String group, QueryRequest<?> request) {
                before.incrementAndGet(); return request;
            }
            @Override public Object selectAfter(String group, BaseRequest<?> request, List<Object> rows) {
                after.incrementAndGet(); return List.of(Map.of("id", "page-after"));
            }
        };
        RouteSession route = route(filter, mysql);
        Page<Map> raw = new Page<>(List.of(Map.of("id", "raw")), 99);
        raw.putValue("summary", "kept");
        doReturn(raw).when(mysql).page(any());

        Page<Map> result = route.page(query());
        assertThat(result.getList()).containsExactly(Map.of("id", "page-after"));
        assertThat(result.getTotal()).isEqualTo(99);
        assertThat(result.getExtra()).containsEntry("summary", "kept");
        assertThat(raw.getList()).containsExactly(Map.of("id", "raw"));
        assertThat(before).hasValue(1); assertThat(after).hasValue(1);
    }

    @Test void numericAfterResultsAreReturnedAndImmediateAfterFailureRollsBack() {
        EntitySession mysql = node("mysql", JoinCluster.master);
        AtomicBoolean fail = new AtomicBoolean();
        IFilter filter = new IFilter() {
            @Override public Object after(String group, BaseRequest request, Object value) {
                if (fail.get()) throw new IllegalStateException("后处理失败");
                return ((Integer) value) + 10;
            }
        };
        RouteSession route = route(filter, mysql);
        doReturn(2).when(mysql).count(any());
        doReturn(1).when(mysql).update(any());
        assertThat(route.count(query())).isEqualTo(12);
        assertThat(route.update(ExecuteRequest.ofUpdate(User.class))).isEqualTo(11);
        fail.set(true);
        assertThatThrownBy(() -> route.update(ExecuteRequest.ofUpdate(User.class))).hasMessage("后处理失败");
        verify(mysql).rollbackTransaction();
        assertThat(route.isOpenTransaction()).isFalse();
    }

    @Test void queueEnhancesBeforeOnlyAtEnqueueAndAfterOnlyAtExecutionWithoutRetryingAfterFailure() {
        EntitySession mysql = node("mysql", JoinCluster.master);
        AtomicInteger before = new AtomicInteger(); AtomicInteger after = new AtomicInteger();
        IFilter filter = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                before.incrementAndGet(); return request;
            }
            @Override public Object after(String group, BaseRequest request, Object value) {
                after.incrementAndGet(); throw new IllegalStateException("通知失败，不重试已成功写入");
            }
        };
        RouteSession route = route(filter, mysql);
        doReturn(1).when(mysql).update(any());
        DataProperties.QueueProperties properties = new DataProperties.QueueProperties();
        properties.setFlushInterval(Duration.ofDays(1));
        try (WriteQueueManager queue = new WriteQueueManager(properties)) {
            route.setWriteQueueManager(queue);
            ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class);
            request.addRequest(new UpdateWrapper<User>().table("users_202610")).queue();
            assertThat(route.update(request)).isZero();
            assertThat(before).hasValue(1); assertThat(after).hasValue(0);
            assertThat(queue.flush()).isEqualTo(1);
            assertThat(before).hasValue(1); assertThat(after).hasValue(1);
            assertThat(queue.pending()).isZero();
            verify(mysql).commitTransaction(); verify(mysql, never()).rollbackTransaction();
        }
    }

    @Test void dynamicTableControlsSqlCacheKeyAndNamespaceAndCacheHitsStillRunAfter() {
        SqlProbe session = new SqlProbe();
        MemoryCache cache = new MemoryCache();
        AtomicReference<String> table = new AtomicReference<>("users_202610");
        AtomicInteger after = new AtomicInteger();
        IFilter filter = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                return ((QueryRequest<?>) request).copy(select(table.get()));
            }
            @Override public Object after(String group, BaseRequest request, Object value) {
                after.incrementAndGet();
                List<Map> rows = (List<Map>) value;
                rows.getFirst().put("id", "enhanced");
                return new ArrayList<>(rows);
            }
        };
        RouteSession route = route(filter, session);
        route.setQueryCache(cache); session.setQueryCache(cache);
        try {
            assertThat(route.select(query().cache()).getFirst()).containsEntry("id", "enhanced");
            route.select(query().cache());
            assertThat(session.loads).isEqualTo(1);
            assertThat(session.sql).contains("users_202610");
            table.set("users_202611");
            route.select(query().cache());
            assertThat(session.loads).isEqualTo(2);
            assertThat(cache.values).hasSize(2);
            assertThat(cache.resources).containsExactlyInAnyOrder("users_202610", "users_202611");
            assertThat(after).hasValue(3);
            for (Object value : cache.values.values())
                assertThat((Map) ((List) value).getFirst()).containsEntry("id", "raw");
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    @Test void dynamicWriteInvalidatesOnlyItsFinalPhysicalTable() {
        EntitySession mysql = node("mysql", JoinCluster.master);
        IFilter filter = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                for (var wrapper : ((ExecuteRequest<?>) request).getWrappers())
                    ((UpdateWrapper<?>) wrapper).table("users_202610");
                return request;
            }
        };
        MemoryCache cache = new MemoryCache();
        RouteSession route = route(filter, mysql);
        route.setQueryCache(cache);
        doReturn(1).when(mysql).update(any());
        ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class);
        request.addRequest(new UpdateWrapper<User>().table("users"));
        route.update(request);
        assertThat(cache.invalidated).containsExactly("users_202610");
    }

    @JoinTable("users") private static class User { private String id; }

    private static final class SqlProbe extends JdbcSession {
        int loads; String sql;
        SqlProbe() {
            super(mock(DataSource.class));
            setGroup("mysql"); setCoderManager(new JdbcCoderManager());
            setProxyService(EntityProxyService.newInstance());
        }
        @Override protected List<IDataBuffer> executeSelect(ParseResult result) {
            loads++; sql = ((SqlNode) result.getRequest()).getSql();
            IDataBuffer row = createDecoderBuffer(); row.put("id", "raw");
            return List.of(row);
        }
    }

    private static final class MemoryCache implements QueryCache {
        final Map<CacheKey, Object> values = new HashMap<>();
        final Set<String> resources = new LinkedHashSet<>();
        final Set<String> invalidated = new LinkedHashSet<>();
        @Override public CacheKey scope(CacheKey key, Set<CacheNamespace> namespaces, Duration ttl) {
            namespaces.forEach(namespace -> resources.add(namespace.resource())); return key;
        }
        @Override public CacheLookup get(CacheKey key, CacheValueType type) {
            return values.containsKey(key) ? CacheLookup.hit(values.get(key)) : CacheLookup.miss();
        }
        @Override public void put(CacheKey key, CacheValueType type, Object value, Duration ttl) { values.put(key, value); }
        @Override public void invalidate(Set<CacheNamespace> namespaces) {
            namespaces.forEach(namespace -> invalidated.add(namespace.resource()));
        }
    }
}

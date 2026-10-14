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
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.queue.WriteQueueManager;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteSessionWriteQueueTest {

    @Test
    void queuedWriteReturnsZeroAndInvalidatesCacheOnlyAfterFlush() {
        RecordingCache cache = new RecordingCache();
        RecordingSession recording = new RecordingSession();
        EntitySession session = recording.session();
        try (WriteQueueManager queue = queue()) {
            RouteSession route = route(session, cache, new IFilterManager() { }, queue);
            ExecuteRequest<User> request = ExecuteRequest.ofInsert(User.class);
            request.addRequest(new TestNode("one")).queue();

            assertThat(route.insert(request)).isZero();
            assertThat(queue.pending()).isEqualTo(1);
            assertThat(cache.namespaces).isEmpty();
            assertThat(recording.inserts).isZero();

            assertThat(queue.flush()).isEqualTo(1);
            assertThat(queue.pending()).isZero();
            assertThat(cache.namespaces).extracting(CacheNamespace::resource).containsExactly("users");
            assertThat(recording.inserts).isOne();
        }
    }

    @Test
    void replacementFilterKeepsQueueOptionAndUsesReplacementRequestType() {
        RecordingSession recording = new RecordingSession();
        EntitySession session = recording.session();
        IFilterManager filters = new IFilterManager() {
            @Override
            public BaseRequest before(String name, BaseRequest request) {
                ExecuteRequest<User> replacement = ExecuteRequest.ofUpdate(User.class);
                replacement.addRequest(new TestNode("logical-delete"));
                return replacement;
            }
        };
        try (WriteQueueManager queue = queue()) {
            RouteSession route = route(session, new RecordingCache(), filters, queue);
            ExecuteRequest<User> request = ExecuteRequest.ofDelete(User.class);
            request.addRequest(new TestNode("delete")).clearCache(false).queue();
            assertThat(route.delete(request)).isZero();
            queue.flush();

            assertThat(recording.updates).isOne();
            assertThat(recording.deletes).isZero();
            assertThat(recording.lastRequest.getRequestType()).isEqualTo(RequestType.update);
            assertThat(recording.lastRequest.getWrappers()).hasSize(1);
            assertThat(recording.lastRequest.getClearCache()).isFalse();
        }
    }

    @Test
    void queuedWriteIsRejectedInsideRouteTransaction() {
        EntitySession session = new RecordingSession().session();
        try (WriteQueueManager queue = queue()) {
            RouteSession route = route(session, new RecordingCache(), new IFilterManager() { }, queue);
            ExecuteRequest<User> request = ExecuteRequest.ofUpdate(User.class);
            request.addRequest(new TestNode("update")).queue();

            route.startTransaction();
            try {
                assertThatThrownBy(() -> route.update(request))
                        .isInstanceOf(NoticeException.class)
                        .hasMessageContaining("事务外");
                assertThat(queue.pending()).isZero();
            } finally {
                route.rollbackTransaction();
            }
        }
    }

    private static WriteQueueManager queue() {
        DataProperties.QueueProperties properties = new DataProperties.QueueProperties();
        properties.setFlushInterval(Duration.ofDays(1));
        return new WriteQueueManager(properties);
    }

    private static RouteSession route(EntitySession session, QueryCache cache,
                                      IFilterManager filters, WriteQueueManager queue) {
        RouteSession route = RouteSession.builder().filterManager(filters).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.setQueryCache(cache);
        route.setWriteQueueManager(queue);
        route.registerSession(session);
        return route;
    }

    private record TestNode(String value) implements INode {
    }

    private static final class RecordingSession {
        int inserts;
        int updates;
        int deletes;
        ExecuteRequest<?> lastRequest;

        EntitySession session() {
            return (EntitySession) Proxy.newProxyInstance(
                    EntitySession.class.getClassLoader(),
                    new Class<?>[]{EntitySession.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "group" -> arguments == null || arguments.length == 0 ? "mysql" : List.of();
                        case "cluster" -> JoinCluster.master;
                        case "weight" -> 1;
                        case "ping" -> true;
                        case "isOpenTransaction", "isRollbackOnly", "supportsParallelWrites" -> false;
                        case "insert" -> record((ExecuteRequest<?>) arguments[0], RequestType.insert);
                        case "update" -> record((ExecuteRequest<?>) arguments[0], RequestType.update);
                        case "delete" -> record((ExecuteRequest<?>) arguments[0], RequestType.delete);
                        case "createTableSql", "select" -> List.of();
                        case "count" -> 0;
                        default -> null;
                    });
        }

        private int record(ExecuteRequest<?> request, RequestType type) {
            lastRequest = request;
            switch (type) {
                case insert -> inserts++;
                case update -> updates++;
                case delete -> deletes++;
                default -> throw new IllegalArgumentException("Not a write: " + type);
            }
            return request.getWrappers().size();
        }
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

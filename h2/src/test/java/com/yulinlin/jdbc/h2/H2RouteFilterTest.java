package com.yulinlin.jdbc.h2;

import com.yulinlin.common.filter.TableLogicFilter;
import com.yulinlin.common.model.*;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.cache.*;
import com.yulinlin.data.core.event.IInitEvent;
import com.yulinlin.data.core.filter.*;
import com.yulinlin.data.core.queue.WriteQueueManager;
import com.yulinlin.data.core.request.*;
import com.yulinlin.data.core.session.*;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.h2.H2SelectEachTest.Initialized;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fact;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fixture;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class H2RouteFilterTest {
    @Test void listPageGroupCountAndExplainUseQueryHooksAtTheRouteBoundary() throws Exception {
        try (var f = new Fixture("")) {
            var before = new AtomicInteger(); var rows = new AtomicInteger();
            var filters = new SimpFilterManager(List.of(new InitFilter(), new IQueryFilter() {
                @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) {
                    before.incrementAndGet(); return request;
                }
                @Override public void afterRow(String group, QueryRequest<?> request, Object row) { rows.incrementAndGet(); }
            }));
            new SessionUtil(f.route(filters));
            var query = ModelSelectWrapper.newInstance(Initialized.class).orderByAsc("id");
            var list = query.selectList();
            assertEquals(10, list.size()); assertTrue(list.stream().allMatch(r -> r.getInitCalls() == 1));
            assertEquals(1, before.get()); assertEquals(10, rows.get());
            var page = query.page(2, 3).selectPage();
            assertEquals(10, page.getTotal()); assertEquals(3, page.getList().size());
            assertTrue(page.getList().stream().allMatch(r -> r.getInitCalls() == 1));
            assertEquals(2, before.get()); assertEquals(13, rows.get(), "internal page count/select cannot enhance twice");
            assertEquals(10, query.count());
            assertFalse(query.explain().isEmpty()); assertFalse(query.explainCount().isEmpty());
            assertEquals(5, before.get()); assertEquals(13, rows.get(), "count/plans have no entity rows");
            var grouped = ModelGroupWrapper.newInstance(Grouped.class).selectList();
            assertEquals(2, grouped.size()); assertTrue(grouped.stream().allMatch(r -> r.initCalls == 1));
            assertEquals(15, rows.get());
            assertTrue(ModelSelectWrapper.newInstance(Initialized.class).eq("id", -1).selectList().isEmpty());
            assertEquals(15, rows.get());
            assertTrue(f.session.select(query.getRequest()).stream().allMatch(r -> r.getInitCalls() == 0),
                    "direct EntitySession must not execute RouteSession filters");
        }
    }

    @Test void cacheHitsReapplyRowInitializationWithoutCachingMutatedRows() throws Exception {
        try (var f = new Fixture("")) {
            f.session.setQueryCache(new MemoryCache());
            var before = new AtomicInteger();
            new SessionUtil(f.route(new SimpFilterManager(List.of(new InitFilter(), new IQueryFilter() {
                @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) {
                    before.incrementAndGet(); return request;
                }
            }))));
            var query = ModelSelectWrapper.newInstance(Initialized.class).eq("id", 1).cache();
            var first = query.selectList().getFirst(); first.setProjectCode("modified-by-caller");
            var second = query.selectList().getFirst();
            assertNotSame(first, second); assertEquals("123", second.getProjectCode());
            assertEquals(1, second.getInitCalls()); assertEquals("123", second.getInitializedProject());
            assertEquals(2, before.get()); assertEquals(1, f.events.size());
        }
    }

    @Test void queryReplacementKeepsHintsAndRunsExactlyOneRowHookBeforeConsumer() throws Exception {
        try (var f = new Fixture("")) {
            var before = new AtomicInteger();
            new SessionUtil(f.route(new SimpFilterManager(List.of(new InitFilter(), new IQueryFilter() {
                @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) {
                    before.incrementAndGet();
                    var select = new SelectWrapper<>().table("repro_minute");
                    select.fields().field("id").field("projectCode");
                    select.where().eq("id", 1);
                    return QueryRequest.newInstance(Initialized.class, select);
                }
                @Override public void afterRow(String group, QueryRequest<?> request, Object row) {
                    ((Initialized) row).setProjectCode("enhanced");
                }
            }, new IWriteFilter() {
                @Override public boolean test(String group, BaseRequest request) { fail("write filter must not see a query"); return true; }
            }))));
            assertEquals(1, ModelSelectWrapper.newInstance(Initialized.class).useIndex("idx_repro_project_time")
                    .selectEach(row -> {
                        assertEquals(1, row.getId()); assertEquals(1, row.getInitCalls());
                        assertEquals("enhanced", row.getInitializedProject());
                    }));
            assertEquals(1, before.get()); assertTrue(f.lastSql().getSql().contains("USE INDEX"));
        }
    }

    @Test void logicalDeleteAndWriteNotificationsUseFinalRequestAndRetainTransactionTiming() throws Exception {
        try (var f = new Fixture("")) {
            var calls = new ArrayList<String>();
            var manager = new SimpFilterManager(List.of(new TableLogicFilter().register(Fact.class, "incomplete", 1, 0),
                    new IWriteFilter() {
                        @Override public int order() { return 1; }
                        @Override public ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) {
                            calls.add("before:" + request.getRequestType()); return request;
                        }
                        @Override public void afterWrite(String group, ExecuteRequest<?> request, int count) {
                            assertTrue(SessionUtil.route().isOpenTransaction());
                            calls.add("after:" + request.getRequestType() + ":" + count);
                            SessionUtil.route().afterCommit(() -> calls.add("commit"));
                        }
                    }));
            new SessionUtil(f.route(manager));
            assertEquals(1, ModelDeleteWrapper.newInstance(Fact.class).eq("id", 1).execute());
            assertEquals(List.of("before:update", "after:update:1", "commit"), calls);
            assertEquals(7, ModelSelectWrapper.newInstance(Fact.class).count());
            assertEquals(10, f.session.count(ModelSelectWrapper.newInstance(Fact.class).getRequest()), "physical row still exists");
        }
    }

    @Test void insertUpdateDeleteAndFailedSqlKeepActualWriteNotifications() throws Exception {
        try (var f = new Fixture("")) {
            var counts = new ArrayList<Integer>(); var types = new ArrayList<RequestType>();
            new SessionUtil(f.route(new SimpFilterManager(List.of(new IWriteFilter() {
                @Override public void afterWrite(String group, ExecuteRequest<?> request, int affectedRows) {
                    types.add(request.getRequestType()); counts.add(affectedRows);
                }
            }, new IQueryFilter() {
                @Override public boolean test(String group, BaseRequest request) { assertInstanceOf(QueryRequest.class, request); return true; }
            }))));
            Fact row = new Fact(); row.setId(100L); row.setProjectCode("new");
            assertEquals(1, ModelInsertWrapper.newInstance(row).execute());
            assertThrows(RuntimeException.class, () -> ModelInsertWrapper.newInstance(row).execute());
            assertEquals(1, ModelUpdateWrapper.newInstance(Fact.class).field("projectCode", "changed").eq("id", 100).execute());
            assertEquals(0, ModelUpdateWrapper.newInstance(Fact.class).field("projectCode", "changed").eq("id", -1).execute());
            assertEquals(1, ModelDeleteWrapper.newInstance(Fact.class).eq("id", 100).execute());
            assertEquals(List.of(RequestType.insert, RequestType.update, RequestType.update, RequestType.delete), types);
            assertEquals(List.of(1, 1, 0, 1), counts);
            assertEquals(10, ModelSelectWrapper.newInstance(Fact.class).count());
        }
    }

    @Test void ordinaryWriteHookFailureRollsBackButQueuedHookFailureDoesNotRetryExecutedWrite() throws Exception {
        try (var f = new Fixture("")) {
            var before = new AtomicInteger(); var after = new AtomicInteger();
            var failure = new IllegalArgumentException("afterWrite failure");
            var route = f.route(new SimpFilterManager(List.of(new IWriteFilter() {
                @Override public ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) {
                    before.incrementAndGet(); return request;
                }
                @Override public void afterWrite(String group, ExecuteRequest<?> request, int affectedRows) {
                    after.incrementAndGet(); throw failure;
                }
            })));
            new SessionUtil(route);
            assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> ModelUpdateWrapper.newInstance(Fact.class).field("projectCode", "rollback").eq("id", 1).execute()));
            assertEquals("123", ModelSelectWrapper.newInstance(Fact.class).eq("id", 1).selectOne().getProjectCode());
            var properties = new DataProperties.QueueProperties(); properties.setFlushInterval(Duration.ofHours(1));
            try (var queue = new WriteQueueManager(properties)) {
                route.setWriteQueueManager(queue);
                ModelUpdateWrapper.newInstance(Fact.class).field("projectCode", "queued").eq("id", 1).queue().execute();
                assertEquals(2, before.get()); assertEquals(1, after.get(), "not called at enqueue time");
                assertEquals(1, queue.flush()); assertEquals(0, queue.pending());
                assertEquals(2, before.get()); assertEquals(2, after.get());
                assertEquals(0, queue.flush()); assertEquals(2, after.get());
                assertEquals("queued", ModelSelectWrapper.newInstance(Fact.class).eq("id", 1).selectOne().getProjectCode());
            }
        }
    }

    @Data @JoinTable("repro_minute") public static class Grouped implements IInitEvent {
        @JoinAggregations private String projectCode;
        @JoinField(name = "id") @JoinMetrics(MetricsEnum.count) private Long total;
        @JoinField(exist = false) private int initCalls;
        @Override public void init() { initCalls++; }
    }

    private static final class MemoryCache implements QueryCache {
        final Map<CacheKey, Object> values = new HashMap<>();
        @Override public CacheLookup get(CacheKey key, CacheValueType type) {
            return values.containsKey(key) ? CacheLookup.hit(values.get(key)) : CacheLookup.miss();
        }
        @Override public void put(CacheKey key, CacheValueType type, Object value, Duration ttl) { values.put(key, value); }
    }
}

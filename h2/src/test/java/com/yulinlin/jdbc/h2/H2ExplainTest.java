package com.yulinlin.jdbc.h2;

import com.yulinlin.common.model.ModelGroupWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.cache.*;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.request.*;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fixture;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fact;
import com.yulinlin.jdbc.h2.H2UseIndexTest.MinuteSum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class H2ExplainTest {
    private static final String INDEX = "idx_repro_project_time";

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void modelListAndCountExplainUseExactlyTheNormalSqlAndParameters(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            var query = ModelSelectWrapper.newInstance(Fact.class).eq("projectCode", "123")
                    .orderByAsc("id").page(2, 3).useIndex(INDEX);
            query.getRequest().autoUpdate(false);
            assertEquals(3, query.selectList().size());
            String listSql = f.lastSql().getSql();
            var listParams = f.lastSql().getList();
            assertNativePlan(query.explain());
            assertEquals("EXPLAIN " + listSql, f.lastSql().getSql());
            assertEquals(listParams, f.lastSql().getList());
            assertEquals(9, query.count());
            String countSql = f.lastSql().getSql();
            assertTrue(countSql.startsWith("select count(1) as total from \"repro_minute\""));
            assertNativePlan(query.explainCount());
            assertEquals("EXPLAIN " + countSql, f.lastSql().getSql());
            assertFalse(countSql.toLowerCase().contains("limit"));
            assertFalse(countSql.toLowerCase().contains("order by"));
            assertTrue(countSql.contains("USE INDEX"));
            assertEquals(ParseType.explain, f.events.getLast().requests().getFirst().getType());
            assertEquals(3, query.selectList().size());
            assertEquals(listSql, f.lastSql().getSql(), "Explaining must not replace the original query");
        }
    }

    @Test
    void groupedPageAndGroupCountRetainHavingAndAliasExpansion() throws Exception {
        try (var f = new Fixture("")) {
            var query = ModelGroupWrapper.newInstance(MinuteSum.class).eq("projectCode", "123")
                    .having(h -> h.gt("amount", 2)).orderByAsc("bucket").page(2, 2).useIndex(INDEX);
            query.getRequest().autoUpdate(false);
            query.selectList();
            String listSql = f.lastSql().getSql();
            var params = f.lastSql().getList();
            assertNativePlan(query.explain());
            assertEquals("EXPLAIN " + listSql, f.lastSql().getSql());
            assertEquals(params, f.lastSql().getList());
            query.count();
            String countSql = f.lastSql().getSql();
            assertTrue(countSql.startsWith("select count(1) as total from ( select "));
            assertNativePlan(query.explainCount());
            assertEquals("EXPLAIN " + countSql, f.lastSql().getSql());
            assertTrue(countSql.contains("group by"));
            assertTrue(countSql.contains("having"));
            assertFalse(countSql.toLowerCase().contains("limit"));
            assertFalse(countSql.toLowerCase().contains("order by"));
        }
    }

    @Test
    void beforeFilterReplacementKeepsHintsRootAndRoutingButDoesNotFilterPlanRows() throws Exception {
        try (var f = new Fixture("")) {
            AtomicInteger before = new AtomicInteger();
            var route = f.route(new IFilterManager() {
                @Override public BaseRequest before(String group, BaseRequest request) {
                    assertEquals("h2", group);
                    before.incrementAndGet();
                    var select = new SelectWrapper<>().table("repro_minute").page(2, 2);
                    select.fields().field("${column}");
                    select.where().eq("projectCode", "123");
                    return QueryRequest.newInstance(Fact.class, select);
                }
                @Override public Object after(String group, BaseRequest request, Object data) {
                    fail("Native plans must not be passed to entity-result filters");
                    return data;
                }
            });
            new SessionUtil(route);
            var request = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute"))
                    .useIndex(INDEX).autoUpdate(true);
            request.setSession("h2");
            request.setRoot(Map.of("column", "amount"));
            assertNativePlan(request.explain());
            assertTrue(f.lastSql().getSql().contains("select \"amount\""));
            assertTrue(f.lastSql().getSql().contains("USE INDEX"));
            assertEquals(List.of("123"), f.lastSql().getList());
            assertNativePlan(request.explainCount());
            assertEquals(2, before.get());
            assertEquals(2, f.events.size());
            assertFalse(route.isOpenTransaction(), "Diagnostics do not require entity auto-update transactions");
        }
    }

    @Test
    void diagnosticsBypassCacheAndKeepTheTransactionUsable() throws Exception {
        try (var f = new Fixture("")) {
            f.session.setQueryCache(new QueryCache() {
                @Override public CacheLookup get(CacheKey key, CacheValueType type) { throw new AssertionError("cache read"); }
                @Override public void put(CacheKey key, CacheValueType type, Object value, Duration ttl) { fail("cache write"); }
            });
            var request = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute"))
                    .cache().autoUpdate(true);
            var route = SessionUtil.route();
            route.startTransaction();
            try {
                assertNativePlan(request.explain());
                assertNativePlan(request.explain());
                assertNativePlan(request.explainCount());
                assertEquals(3, f.events.size());
                assertTrue(f.session.isOpenTransaction());
                assertFalse(f.session.isRollbackOnly());
                assertEquals(CacheMode.READ_THROUGH, request.getCacheMode());
            } finally { route.rollbackTransaction(); }
            assertFalse(f.session.isOpenTransaction());
            assertNativePlan(request.explain());
            assertEquals(4, f.events.size());
        }
    }

    @Test
    void joinsAndExplicitCountWrappersKeepTheirQueryStructure() throws Exception {
        try (var f = new Fixture("")) {
            var select = new SelectWrapper<>().table("repro_minute", "a");
            select.fields().field("a.id");
            select.left("repro_minute", "b").eq("b.id", 1L);
            select.where().eq("a.id", 2L);
            var request = QueryRequest.newInstance(Map.class, select);
            assertNativePlan(request.explain());
            assertTrue(f.lastSql().getSql().toLowerCase().contains("left join"));
            var count = request.copy(new CountWrapper(select));
            assertNativePlan(count.explain());
            String sql = f.lastSql().getSql();
            assertNativePlan(count.explainCount());
            assertEquals(sql, f.lastSql().getSql(), "Explicit count must not be wrapped twice");
        }
    }

    @Test
    void rawSqlAndWritesAreRejectedAndDatabaseFailuresRemainVisible() throws Exception {
        try (var f = new Fixture("")) {
            for (String sql : List.of("select 1", "EXPLAIN ANALYZE select 1", "delete from repro_minute")) {
                var request = QueryRequest.newInstance(sql, Map.of(), Map.class);
                assertThrows(UnsupportedOperationException.class, request::explain);
                assertThrows(UnsupportedOperationException.class, request::explainCount);
            }
            var write = QueryRequest.newInstance(Map.class, new UpdateWrapper<>().table("repro_minute"));
            assertThrows(UnsupportedOperationException.class, write::explain);
            assertTrue(f.events.isEmpty());
            var request = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute"))
                    .useIndex("missing_index");
            SQLException error = assertThrows(SQLException.class, request::explain);
            assertSame(error, f.events.getLast().error());
            assertTrue(f.lastSql().getSql().startsWith("EXPLAIN "));
            assertFalse(f.lastSql().getSql().contains("ANALYZE"));
            request.useIndex(INDEX);
            assertNativePlan(request.explain());
            assertEquals(2, f.events.size(), "Route state is released after errors");
        }
    }

    private static void assertNativePlan(List<Map<String, Object>> plan) {
        assertFalse(plan.isEmpty());
        assertEquals(1, plan.getFirst().size());
        String label = plan.getFirst().keySet().iterator().next();
        assertTrue(label.equals("PLAN") || label.equals("plan"));
        assertInstanceOf(String.class, plan.getFirst().get(label));
        assertFalse(plan.getFirst().get(label).toString().contains("scanCount:"), "Only ordinary EXPLAIN");
    }
}

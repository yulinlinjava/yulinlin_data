package com.yulinlin.jdbc.h2;

import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.filter.TableLogicFilter;
import com.yulinlin.data.core.event.IInitEvent;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.filter.*;
import com.yulinlin.data.core.request.*;
import com.yulinlin.data.core.session.*;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fixture;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fact;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class H2SelectEachTest {
    @ParameterizedTest @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void listAndEachKeepMappingParametersOrderingPaginationAndIndexHint(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            var query = ModelSelectWrapper.newInstance(Fact.class).eq("projectCode", "123")
                    .orderByAsc("id").page(2, 3).useIndex("idx_repro_project_time");
            var expected = query.selectList();
            var sql = f.lastSql(); var actual = new ArrayList<Fact>();
            assertEquals(3, query.selectEach(actual::add));
            assertEquals(expected, actual);
            assertEquals(sql.getSql(), f.lastSql().getSql());
            assertEquals(sql.getList(), f.lastSql().getList());
            assertEquals(expected, query.selectList());
            assertEquals(0, ModelSelectWrapper.newInstance(Fact.class).eq("id", -1).selectEach(row -> fail("empty")));
        }
    }

    @Test void rawSqlUsesTheSameScalarAndTemporalDecoders() throws Exception {
        try (var f = new Fixture("")) {
            var request = QueryRequest.newInstance("select \"id\", \"minute_at\" as \"sourceTime\", \"amount\" from \"repro_minute\" where \"id\" = #{id}", Map.of("id", 3), Fact.class);
            var expected = request.selectList(); var actual = new ArrayList<Fact>();
            assertEquals(1, request.selectEach(actual::add));
            assertEquals(expected, actual);
            assertNotNull(actual.getFirst().getSourceTime());
            assertNull(actual.getFirst().getAmount());
        }
    }

    @Test void explicitUnsupportedOptionsAndStreamIncompatibleFiltersAreRejected() throws Exception {
        try (var f = new Fixture("")) {
            var query = ModelSelectWrapper.newInstance(Fact.class);
            assertThrows(IllegalArgumentException.class, () -> query.cache().selectEach(row -> fail()));
            assertThrows(IllegalArgumentException.class, () -> query.cache(false).autoUpdate().selectEach(row -> fail()));
            assertThrows(UnsupportedOperationException.class, () -> QueryRequest.newInstance(Map.class, new GroupWrapper<>().table("repro_minute")).selectEach(row -> fail()));
            assertTrue(f.events.isEmpty());
            var filters = new SimpFilterManager(List.of(new IQueryFilter() {
                @Override public boolean supportsSelectEach() { return false; }
            }));
            var route = f.route(filters);
            assertThrows(UnsupportedOperationException.class, () -> route.selectEach(query.getRequest().autoUpdate(false), row -> fail()));
            assertTrue(f.events.isEmpty());
        }
    }

    @Test void queryRequestReplacementRetainsOptionsAndRunsRowHookBeforeConsumer() throws Exception {
        try (var f = new Fixture("")) {
            var enhanced = new java.util.concurrent.atomic.AtomicInteger();
            var filters = new SimpFilterManager(List.of(new IQueryFilter() {
                @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) {
                    var select = new SelectWrapper<>().table("repro_minute").orderByAsc("id");
                    select.fields().field("id");
                    select.where().eq("projectCode", "other");
                    return QueryRequest.newInstance(Fact.class, select);
                }
                @Override public void afterRow(String group, QueryRequest<?> request, Object row) { enhanced.incrementAndGet(); }
            }));
            var route = f.route(filters); var rows = new ArrayList<Fact>();
            var original = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute")).useIndex("idx_repro_project_time");
            assertEquals(1, route.selectEach(original, row -> {
                assertEquals(1, enhanced.get()); rows.add(row);
            }));
            assertEquals(6, rows.getFirst().getId());
            assertTrue(f.lastSql().getSql().contains("USE INDEX"));
            assertEquals(List.of("other"), f.lastSql().getList());
        }
    }

    @Test void associationsAndAutomaticProxiesRemainExclusiveToListQueries() throws Exception {
        try (var f = new Fixture("")) {
            var properties = new DataProperties(); properties.setAutoUpdate(true);
            f.session.setProxyService(EntityProxyService.newInstance(properties));
            var query = ModelSelectWrapper.newInstance(Related.class).eq("id", 1);
            var rows = new ArrayList<Related>();
            assertEquals(1, query.selectEach(rows::add));
            assertEquals(Related.class, rows.getFirst().getClass());
            assertNull(rows.getFirst().getRelated());
            assertEquals(1, f.events.size());
            rows.getFirst().setProjectCode("memory-only");
            var list = query.autoUpdate(false).selectList();
            assertEquals("123", list.getFirst().getProjectCode());
            assertEquals(1, list.getFirst().getRelated().size());
            assertEquals(3, f.events.size(), "list still loads associations");
        }
    }

    @Test void joinedTransactionRetainsOwnershipAndCallbackFailureAllowsCallerToDecideRollback() throws Exception {
        try (var f = new Fixture("")) {
            var route = SessionUtil.route(); var query = ModelSelectWrapper.newInstance(Fact.class).eq("id", 1);
            var error = new IllegalArgumentException("consumer failed");
            route.transaction(() -> {
                assertSame(error, assertThrows(IllegalArgumentException.class, () -> query.selectEach(row -> { throw error; })));
                assertFalse(route.isRollbackOnly());
                assertTrue(f.session.isOpenTransaction());
                assertEquals(1, query.selectEach(row -> {
                    assertThrows(IllegalStateException.class, () -> query.selectList());
                    assertThrows(IllegalStateException.class, route::commitTransaction);
                    assertThrows(IllegalStateException.class, route::rollbackTransaction);
                    assertThrows(IllegalStateException.class, () -> route.transaction(() -> null));
                }));
                assertEquals(1, query.selectList().size());
                return null;
            });
            assertFalse(f.session.isOpenTransaction());
            assertEquals(1, query.selectEach(row -> { }));
            assertSame(error, f.events.getFirst().error());
        }
    }

    @Test void builtInFiltersKeepLogicalDeletionAndInitializeEachRowBeforeConsumption() throws Exception {
        try (var f = new Fixture("")) {
            var filters = new SimpFilterManager(List.of(new InitFilter(),
                    new TableLogicFilter().register(Initialized.class, "incomplete", 1, 0), new IQueryFilter() {
                        @Override public void afterRow(String group, QueryRequest<?> request, Object row) {
                            ((Initialized) row).setProjectCode("masked");
                        }
                    }));
            new SessionUtil(f.route(filters));
            var query = ModelSelectWrapper.newInstance(Initialized.class).orderByAsc("id");
            var ids = new ArrayList<Long>();
            assertEquals(8, query.selectEach(row -> {
                assertEquals(1, row.getInitCalls());
                assertEquals("masked", row.getInitializedProject());
                ids.add(row.getId());
            }));
            assertFalse(ids.contains(2L)); assertFalse(ids.contains(3L));
            assertEquals(1, f.events.size());
            assertTrue(f.lastSql().getSql().contains("\"incomplete\""));
            var listed = query.selectList();
            assertEquals(ids, listed.stream().map(Initialized::getId).toList());
            assertTrue(listed.stream().allMatch(row -> row.getInitCalls() == 1));
            assertEquals("masked", listed.getFirst().getInitializedProject(), "list and stream share afterRow");
        }
    }

    @Test void initFailureStopsBeforeConsumerAndReleasesTheIterationGuard() throws Exception {
        try (var f = new Fixture("")) {
            new SessionUtil(f.route(new SimpFilterManager(List.of(new InitFilter()))));
            var query = ModelSelectWrapper.newInstance(FailingInit.class).eq("id", 1);
            assertThrows(IllegalArgumentException.class, () -> query.selectEach(row -> fail("init must run first")));
            assertEquals(1, f.events.size());
            assertInstanceOf(IllegalArgumentException.class, f.events.getFirst().error());
            assertEquals(1, ModelSelectWrapper.newInstance(Fact.class).eq("id", 1).selectEach(row -> { }));
        }
    }

    @Test void initCannotReenterOrmAndRowFilterFailureStopsConsumption() throws Exception {
        try (var f = new Fixture("")) {
            new SessionUtil(f.route(new SimpFilterManager(List.of(new InitFilter()))));
            assertThrows(IllegalStateException.class,
                    () -> ModelSelectWrapper.newInstance(QueryingInit.class).eq("id", 1).selectEach(row -> fail()));
            assertEquals(1, f.events.size(), "init must not open a second ORM query");
            var failure = new IllegalArgumentException("row failed");
            var filters = new SimpFilterManager(List.of(new IQueryFilter() {
                @Override public void afterRow(String group, QueryRequest<?> request, Object row) { throw failure; }
            }));
            new SessionUtil(f.route(filters));
            assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> ModelSelectWrapper.newInstance(Fact.class).eq("id", 1).selectEach(row -> fail())));
            new SessionUtil(f.route(IFilterManager.NONE));
            assertEquals(10, ModelSelectWrapper.newInstance(Fact.class).selectList().size());
        }
    }

    @Data @JoinTable("repro_minute") public static class Initialized implements IInitEvent {
        private Long id;
        private String projectCode;
        @JoinField(exist = false) private int initCalls;
        @JoinField(exist = false) private String initializedProject;
        @Override public void init() { initCalls++; initializedProject = projectCode; }
    }
    @Data @JoinTable("repro_minute") public static class FailingInit implements IInitEvent {
        private Long id;
        @Override public void init() { throw new IllegalArgumentException("init failure"); }
    }
    @Data @JoinTable("repro_minute") public static class QueryingInit implements IInitEvent {
        private Long id;
        @Override public void init() { ModelSelectWrapper.newInstance(Fact.class).count(); }
    }

    @Data @JoinTable("repro_minute") public static class Related {
        @JoinMeta(primaryKey = true) private Long id;
        private String projectCode;
        @JoinField(exist = false) @JoinQuery(value = "${id}") private List<Fact> related;
    }
}

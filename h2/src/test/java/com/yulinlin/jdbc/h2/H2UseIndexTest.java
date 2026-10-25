package com.yulinlin.jdbc.h2;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yulinlin.common.model.ModelGroupWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.cache.*;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.*;
import com.yulinlin.data.core.session.*;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import com.yulinlin.jdbc.log.JdbcExecutionLog;
import com.yulinlin.jdbc.session.SqlNode;
import com.yulinlin.jdbc.sql.SqlParseManager;
import lombok.Data;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real H2 2.3.232, public model/request/session paths, isolated disposable databases only. */
@ResourceLock("SessionUtil.route")
@ResourceLock("com.yulinlin.jdbc.log.JdbcExecutionLog.logger")
class H2UseIndexTest {
    private static final String INDEX = "idx_repro_project_time";
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 11, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void modelSelectOneListPageAndCountPreserveResultsAndBoundaries(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            var query = ModelSelectWrapper.newInstance(Fact.class)
                    .eq("projectCode", "123").gte("sourceTime", START).lt("sourceTime", END)
                    .in("metricCode", List.of("m1", "m2")).eq("collectionKind", "API").orderByAsc("id");
            query.getRequest().autoUpdate(false);
            List<Fact> original = query.selectList();
            assertEquals(List.of(1L, 2L, 3L, 4L, 10L), original.stream().map(Fact::getId).toList());
            SqlNode plain = f.lastSql();
            assertEquals(5, query.count(), "Unhinted count works in upper/lower identifier modes too");
            f.events.clear();
            query.useIndex(INDEX);
            assertEquals(original, query.selectList());
            assertEquals(plain.getSql(), f.lastSql().getSql().replace(hint(), ""));
            assertEquals(plain.getList(), f.lastSql().getList());
            assertTrue(f.lastSql().getSql().contains("?"));
            assertFalse(f.lastSql().getSql().contains("2026-10-24"));
            assertEquals(5, query.count());
            var page = query.selectPage(2, 2);
            assertEquals(5, page.getTotal());
            assertEquals(List.of(3L, 4L), page.getList().stream().map(Fact::getId).toList());
            assertEquals(1L, query.selectOne().getId());
            assertTrue(f.events.stream().allMatch(e -> sql(e).getSql().contains(hint())));
            assertTrue(f.events.stream().anyMatch(e -> e.requests().getFirst().getType() == ParseType.count));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void modelGroupPreservesSumMaxNullsHavingAndGroupPageTotals(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            var query = ModelGroupWrapper.newInstance(MinuteSum.class)
                    .eq("projectCode", "123").gte("sourceTime", START).lt("sourceTime", END)
                    .in("metricCode", List.of("m1", "m2")).eq("collectionKind", "API")
                    .orderByAsc("metricCode").orderByAsc("bucket");
            query.getRequest().autoUpdate(false);
            List<MinuteSum> original = query.selectList();
            assertEquals(4, original.size());
            assertEquals(Arrays.asList(5.0, 4.0, null, 0.0), original.stream().map(MinuteSum::getAmount).toList());
            assertEquals(Arrays.asList(1, 0, null, 0), original.stream().map(MinuteSum::getIncomplete).toList());
            SqlNode plain = f.lastSql();
            assertEquals(4, query.count(), "Unhinted group count keeps the same total protocol");
            query.useIndex(INDEX);
            f.events.clear();
            assertEquals(original, query.selectList());
            assertEquals(plain.getSql(), f.lastSql().getSql().replace(hint(), ""));
            assertEquals(plain.getList(), f.lastSql().getList());
            assertEquals(4, query.count(), "Count groups, not the 5 matching input rows");
            query.page(2, 2);
            var page = query.selectPage();
            assertEquals(4, page.getTotal());
            assertEquals(original.subList(2, 4), page.getList());
            query.having(h -> h.gt("amount", 0));
            assertEquals(2, query.count());
            query.page(1, 10);
            assertEquals(original.subList(0, 2), query.selectList());
            assertEquals(original.getFirst(), query.selectOne());
            assertTrue(f.events.stream().allMatch(e -> occurrences(sql(e).getSql(), hint()) == 1));
        }
    }

    @Test
    void generatedModelSqlUsesTheRequestedIndexAndKeepsTheSyntheticAggregateResults() throws Exception {
        try (var f = new Fixture(";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")) {
            // Only this fixture's fresh in-memory database; no business files or shared tables.
            try (var statement = f.anchor.createStatement()) {
                statement.execute("DELETE FROM \"repro_minute\"");
                statement.execute("INSERT INTO \"repro_minute\" SELECT t.X * 16 + m.X * 2 + d.X, '123',"
                        + " CONCAT('m', m.X), DATEADD('MINUTE', t.X, TIMESTAMP '2026-10-24 00:00:00'),"
                        + " CONCAT('d', d.X), 'API', CAST(MOD(t.X, 10) + m.X / 10.0 AS DOUBLE), 0"
                        + " FROM SYSTEM_RANGE(0, 2399) t CROSS JOIN SYSTEM_RANGE(1, 8) m CROSS JOIN SYSTEM_RANGE(1, 2) d");
                statement.execute("ANALYZE TABLE \"repro_minute\"");
            }
            var query = ModelGroupWrapper.newInstance(MinuteSum.class)
                    .eq("projectCode", "123").gte("sourceTime", START).lt("sourceTime", END)
                    .in("metricCode", List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8"))
                    .eq("collectionKind", "API").orderByAsc("metricCode").orderByAsc("bucket").cache(false);
            query.getRequest().autoUpdate(false);
            var original = query.selectList();
            assertEquals(480, original.size());
            query.useIndex(INDEX);
            assertEquals(original, query.selectList());
            SqlNode executed = f.lastSql();
            try (var statement = f.anchor.prepareStatement("EXPLAIN ANALYZE " + executed.getSql())) {
                for (int i = 0; i < executed.getList().size(); i++) statement.setObject(i + 1, executed.getList().get(i));
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    String plan = result.getString(1);
                    assertTrue(plan.contains("/* public." + INDEX + ":"), plan);
                    var scans = java.util.regex.Pattern.compile("scanCount: (\\d+)").matcher(plan);
                    assertTrue(scans.find(), plan);
                    assertTrue(Integer.parseInt(scans.group(1)) < 38400 / 2, plan);
                }
            }
            assertEquals(480, query.count());
        }
    }

    @Test
    void emptyWindowsAndUnhintedQueriesAfterHintedQueriesStayIndependent() throws Exception {
        try (var f = new Fixture("")) {
            var select = new SelectWrapper<>().table("repro_minute");
            select.fields().field("id");
            select.where().lt("sourceTime", START.minusDays(1));
            var hinted = QueryRequest.newInstance(Fact.class, select).useIndex(INDEX);
            assertEquals(0, f.session.count(hinted));
            assertTrue(f.session.page(hinted).getList().isEmpty());
            var plain = QueryRequest.newInstance(Fact.class, select);
            assertEquals(0, f.session.count(plain));
            assertFalse(f.lastSql().getSql().contains("USE INDEX"));
            var group = ModelGroupWrapper.newInstance(MinuteSum.class).useIndex(INDEX)
                    .lt("sourceTime", START.minusDays(1));
            assertEquals(0, group.count());
            assertTrue(group.selectList().isEmpty());
            assertEquals(0, group.page(1, 2).selectPage().getTotal());
        }
    }

    @Test
    void aliasAndExplicitCountUseHintOnThePhysicalTableOnly() throws Exception {
        try (var f = new Fixture("")) {
            var select = new SelectWrapper<>().table("repro_minute", "r").page(2, 2);
            select.fields().field("id");
            select.where().eq("r.project_code", "123");
            var request = QueryRequest.newInstance(Map.class, select).useIndex(INDEX);
            assertEquals(9, f.session.count(request));
            assertTrue(f.lastSql().getSql().startsWith("select count(1) as total from \"repro_minute\" \"r\"" + hint()));
            assertFalse(f.lastSql().getSql().contains("limit"));
            assertEquals(9, f.session.count(request.copy(new CountWrapper(select))));
            assertEquals(2, f.session.select(request).size());
            assertEquals(1, occurrences(f.lastSql().getSql(), hint()));
        }
    }

    @Test
    void cacheSeparatesHintsButStillSharesCountsAcrossPages() throws Exception {
        try (var f = new Fixture("")) {
            var cache = new MemoryCache();
            f.session.setQueryCache(cache);
            var select = new SelectWrapper<>().table("repro_minute").page(1, 2).orderByAsc("id");
            select.fields().field("id");
            select.where().eq("projectCode", "123");
            var plain = QueryRequest.newInstance(Fact.class, select).cache().autoUpdate(false);
            var hinted = plain.copy(select).useIndex(INDEX);
            assertEquals(9, f.session.count(plain));
            assertEquals(9, f.session.count(hinted));
            assertEquals(2, f.events.size());
            assertEquals(9, f.session.count(hinted));
            select.page(2, 2);
            assertEquals(9, f.session.count(hinted));
            assertEquals(2, f.events.size());
            assertEquals(f.session.select(plain), f.session.select(hinted));
            assertEquals(4, f.events.size());
            f.session.select(hinted);
            assertEquals(4, f.events.size());
            hinted.useIndex("uk_repro_project_metric_time_dim");
            assertEquals(9, f.session.count(hinted));
            assertEquals(5, f.events.size());
            assertEquals(5, cache.values.size());
            assertNull(plain.getIndexHint(), "Shared wrapper does not share mutable request options");
        }
    }

    @Test
    void routeFilterReplacementRetainsHintsAndRejectsRetargeting() throws Exception {
        try (var f = new Fixture("")) {
            var source = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute"))
                    .useIndex(INDEX).autoUpdate(false);
            var route = f.route(new IFilterManager() {
                @Override public BaseRequest before(String group, BaseRequest original) {
                    var select = new SelectWrapper<>().table("repro_minute");
                    select.fields().field("id");
                    select.where().eq("projectCode", "123");
                    return QueryRequest.newInstance(Fact.class, select);
                }
            });
            assertEquals(9, route.count(source));
            assertTrue(f.lastSql().getSql().contains(hint()));
            var other = f.route(new IFilterManager() {
                @Override public BaseRequest before(String group, BaseRequest original) {
                    return QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("other_table"));
                }
            });
            assertThrows(IllegalArgumentException.class, () -> other.count(source));
            assertEquals(1, f.events.size());
            ((SelectWrapper<?>) source.getWrapper()).table("other_table");
            assertThrows(IllegalArgumentException.class, () -> f.session.count(source));
            assertEquals(1, f.events.size());
        }
    }

    @Test
    void unknownIndexFailsWithoutCachingOrFallback() throws Exception {
        try (var f = new Fixture("")) {
            var cache = new MemoryCache();
            f.session.setQueryCache(cache);
            var request = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute"))
                    .useIndex("missing_index").cache();
            SQLException error = assertThrows(SQLException.class, () -> f.session.count(request));
            assertTrue(error.getMessage().contains("missing_index"));
            assertSame(error, f.events.getFirst().error());
            assertTrue(cache.values.isEmpty());
            assertEquals(1, f.events.size());
        }
    }

    @Test
    void unadaptedDialectIgnoresHintAndStillExecutesAndCachesTheQuery() throws Exception {
        try (var f = new Fixture(";DATABASE_TO_LOWER=TRUE")) {
            f.session.setParseManager(new SqlParseManager());
            var cache = new MemoryCache();
            f.session.setQueryCache(cache);
            var select = new SelectWrapper<>().table("repro_minute");
            select.where().eq("projectCode", "123");
            var request = QueryRequest.newInstance(Fact.class, select).useIndex("missing_index").cache();
            assertEquals(9, f.session.count(request));
            assertFalse(f.lastSql().getSql().contains("USE INDEX"));
            assertFalse(f.lastSql().getSql().contains("missing_index"));
            assertEquals(List.of("123"), f.lastSql().getList());
            assertEquals(9, f.session.count(request));
            assertEquals(1, f.events.size());
            assertEquals(1, cache.values.size());
        }
    }

    @Test
    void rootParametersAndMultipleIndexNamesKeepTheirOriginalEncoding() throws Exception {
        try (var f = new Fixture("")) {
            var select = new SelectWrapper<>().table("repro_minute");
            select.fields().field("${chosenColumn}");
            select.where().eq("id", 1L);
            var request = QueryRequest.newInstance(Map.class, select)
                    .useIndex(INDEX, "uk_repro_project_metric_time_dim", INDEX);
            request.setRoot(Map.of("chosenColumn", "amount"));
            assertEquals(1, f.session.select(request).size());
            assertTrue(f.lastSql().getSql().contains("select \"amount\""));
            assertTrue(f.lastSql().getSql().contains("USE INDEX (\"" + INDEX + "\", \"uk_repro_project_metric_time_dim\")"));
            assertEquals(List.of(1L), f.lastSql().getList());
        }
    }

    @Test
    void transactionsStillBypassCacheAndUseTheSameSessionResource() throws Exception {
        try (var f = new Fixture("")) {
            var cache = new MemoryCache();
            f.session.setQueryCache(cache);
            var request = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute"))
                    .useIndex(INDEX).cache();
            f.session.startTransaction();
            try {
                assertEquals(10, f.session.count(request));
                assertEquals(10, f.session.count(request));
                assertTrue(f.session.isOpenTransaction());
                assertTrue(cache.values.isEmpty());
                assertEquals(2, f.events.size());
            } finally {
                f.session.rollbackTransaction();
            }
            assertFalse(f.session.isOpenTransaction());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void normalAndSlowSqlLogsAndExecutionObserversRetainTheHint(boolean pretty) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(JdbcExecutionLog.class);
        Level oldLevel = logger.getLevel();
        boolean additive = logger.isAdditive();
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
        logger.setAdditive(false);
        try (var f = new Fixture("")) {
            f.session.getProperties().setLog(true);
            f.session.getProperties().setLogSqlPretty(pretty);
            f.session.getProperties().setSlowSqlThreshold(Duration.ofMillis(10));
            var request = QueryRequest.newInstance(Fact.class, new SelectWrapper<>().table("repro_minute")).useIndex(INDEX);
            assertEquals(10, f.session.count(request));
            var event = f.events.getFirst();
            String executed = sql(event).getSql();
            JdbcExecutionLog.print(new SessionExecution(f.session, event.requests(), 20_000_000, null, null, false));
            assertTrue(appender.list.stream().allMatch(e -> e.getFormattedMessage().contains("USE INDEX")));
            assertTrue(appender.list.stream().allMatch(e -> e.getFormattedMessage().contains(INDEX)));
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN));
            assertEquals(executed, sql(event).getSql());
            assertTrue(executed.contains(hint()), "JDBC and observers keep quoted SQL even when logs are pretty");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
            logger.setAdditive(additive);
            appender.stop();
        }
    }

    private static String hint() { return " USE INDEX (\"" + INDEX + "\")"; }
    private static int occurrences(String sql, String fragment) { return (sql.length() - sql.replace(fragment, "").length()) / fragment.length(); }
    private static SqlNode sql(SessionExecution event) { return (SqlNode) event.requests().getFirst().getRequest(); }

    static final class Fixture implements AutoCloseable {
        final H2Session session;
        final Connection anchor;
        final List<SessionExecution> events = new ArrayList<>();
        final RouteSession previous = SessionUtil.route();

        Fixture(String mode) throws Exception {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:use_index_" + UUID.randomUUID() + mode);
            anchor = ds.getConnection();
            try (var s = anchor.createStatement()) {
                s.execute("CREATE TABLE \"repro_minute\" (\"id\" BIGINT PRIMARY KEY, \"project_code\" VARCHAR(30),"
                        + " \"metric_code\" VARCHAR(30), \"minute_at\" TIMESTAMP(6), \"dimension_id\" VARCHAR(30),"
                        + " \"collection_kind\" VARCHAR(8), \"amount\" DOUBLE PRECISION, \"incomplete\" INTEGER)");
                s.execute("CREATE UNIQUE INDEX \"uk_repro_project_metric_time_dim\" ON \"repro_minute\""
                        + " (\"project_code\", \"metric_code\", \"minute_at\", \"dimension_id\")");
                s.execute("CREATE INDEX \"" + INDEX + "\" ON \"repro_minute\" (\"project_code\", \"minute_at\")");
                s.execute("INSERT INTO \"repro_minute\" VALUES"
                        + " (1,'123','m1','2026-10-24 11:00:00','d1','API',2,0),"
                        + " (2,'123','m1','2026-10-24 11:00:00','d2','API',3,1),"
                        + " (3,'123','m2','2026-10-24 11:01:00','d1','API',NULL,NULL),"
                        + " (4,'123','m1','2026-10-24 11:59:59.999999','d1','API',4,0),"
                        + " (5,'123','m1','2026-10-24 12:00:00','d1','API',100,0),"
                        + " (6,'other','m1','2026-10-24 11:00:00','d1','API',100,0),"
                        + " (7,'123','m1','2026-10-24 10:59:59.999999','d1','API',100,0),"
                        + " (8,'123','m1','2026-10-24 11:00:00','d3','SDK',100,0),"
                        + " (9,'123','m3','2026-10-24 11:00:00','d1','API',100,0),"
                        + " (10,'123','m2','2026-10-24 11:05:00','d1','API',0,0)");
            }
            session = new H2Session(ds);
            session.setCoderManager(new H2CoderManager());
            session.setProxyService(EntityProxyService.newInstance());
            session.setGroup("h2");
            session.setExecutionListeners(List.of(events::add));
            new SessionUtil(route(IFilterManager.NONE));
        }

        RouteSession route(IFilterManager filter) {
            var route = RouteSession.builder().filterManager(filter).proxyService(EntityProxyService.newInstance()).build();
            route.setLoadBalance(new RandomLoadBalance());
            route.registerSession(session);
            route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                    DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
            return route;
        }

        SqlNode lastSql() { return sql(events.getLast()); }
        @Override public void close() throws Exception {
            new SessionUtil(previous);
            session.getThreadPoolExecutor().shutdownNow();
            anchor.close();
        }
    }

    private static final class MemoryCache implements QueryCache {
        final Map<CacheKey, Object> values = new HashMap<>();
        @Override public CacheLookup get(CacheKey key, CacheValueType type) {
            return values.containsKey(key) ? CacheLookup.hit(values.get(key)) : CacheLookup.miss();
        }
        @Override public void put(CacheKey key, CacheValueType type, Object value, Duration ttl) { values.put(key, value); }
    }

    @Data @JoinTable("repro_minute")
    public static class Fact {
        private Long id;
        private String projectCode;
        private String metricCode;
        @JoinField(name = "minute_at") private LocalDateTime sourceTime;
        private String dimensionId;
        private String collectionKind;
        private Double amount;
        private Integer incomplete;
    }

    @Data @JoinTable("repro_minute")
    public static class MinuteSum {
        private String projectCode;
        @JoinAggregations private String metricCode;
        private String collectionKind;
        @JoinField(name = "minute_at") private LocalDateTime sourceTime;
        @JoinAggregations(AggregationsEnum.minute) @JoinField(name = "minute_at") private String bucket;
        @JoinMetrics(MetricsEnum.max) private Integer incomplete;
        @JoinMetrics(MetricsEnum.sum) private Double amount;
    }
}

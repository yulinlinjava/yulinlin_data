package com.yulinlin.data.core.session;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheMissException;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.coder.MapCoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheModeTest {

    @Test void noneAlwaysUsesLoaderWithoutTouchingCache() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();
        assertThat(fixture.execute(CacheMode.NONE, () -> "db-" + loads.incrementAndGet())).isEqualTo("db-1");
        assertThat(fixture.execute(CacheMode.NONE, () -> "db-" + loads.incrementAndGet())).isEqualTo("db-2");
        assertThat(fixture.cache.gets).isZero();
        assertThat(fixture.cache.puts).isZero();
    }

    @Test void cacheOnlyNeverFallsBackToLoader() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();
        assertThatThrownBy(() -> fixture.execute(CacheMode.CACHE_ONLY, () -> {
            loads.incrementAndGet(); return "db";
        })).isInstanceOf(CacheMissException.class);
        assertThat(loads).hasValue(0);

        fixture.execute(CacheMode.REFRESH, () -> "cached");
        assertThat(fixture.execute(CacheMode.CACHE_ONLY, () -> "unused")).isEqualTo("cached");
    }

    @Test void readThroughLoadsOnceAndRefreshAlwaysOverwrites() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();
        assertThat(fixture.execute(CacheMode.READ_THROUGH, () -> "db-" + loads.incrementAndGet()))
                .isEqualTo("db-1");
        assertThat(fixture.execute(CacheMode.READ_THROUGH, () -> "db-" + loads.incrementAndGet()))
                .isEqualTo("db-1");
        assertThat(loads).hasValue(1);
        int getsBeforeRefresh = fixture.cache.gets;
        assertThat(fixture.execute(CacheMode.REFRESH, () -> "fresh")).isEqualTo("fresh");
        assertThat(fixture.cache.gets).isEqualTo(getsBeforeRefresh);
        assertThat(fixture.execute(CacheMode.CACHE_ONLY, () -> "unused")).isEqualTo("fresh");
    }

    @Test
    void countCacheIgnoresPageOrderSelectedFieldsAndLock() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();

        SelectWrapper<UserRow> first = select("users", 1);
        first.page(1, 10).orderByAsc("name").fields(fields -> fields.field("id"));

        SelectWrapper<UserRow> second = select("users", 1);
        second.page(20, 200).orderByDesc("createdAt")
                .fields(fields -> fields.field("name").highlight("name"))
                .lock();

        assertThat(fixture.execute(request(first), ParseType.count,
                () -> 40 + loads.incrementAndGet())).isEqualTo(41);
        assertThat(fixture.execute(request(second), ParseType.count,
                () -> 40 + loads.incrementAndGet())).isEqualTo(41);
        assertThat(loads).hasValue(1);
    }

    @Test
    void countCacheKeepsFromAndWhereParametersIsolated() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();

        int first = fixture.execute(request(select("users", 1)), ParseType.count, loads::incrementAndGet);
        int anotherParameter = fixture.execute(request(select("users", 2)), ParseType.count, loads::incrementAndGet);
        int anotherFrom = fixture.execute(request(select("archived_users", 1)), ParseType.count, loads::incrementAndGet);

        assertThat(List.of(first, anotherParameter, anotherFrom)).containsExactly(1, 2, 3);
        assertThat(fixture.cache.values).hasSize(3);
    }

    @Test
    void groupedCountCacheKeepsGroupingMetricsAndHavingIsolatedButIgnoresPageAndOrder() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();

        GroupWrapper<UserRow> first = group("status", "total", 10);
        first.page(1, 10).orderByAsc("status");
        GroupWrapper<UserRow> sameLogicalCount = group("status", "total", 10);
        sameLogicalCount.page(9, 500).orderByDesc("total");
        GroupWrapper<UserRow> anotherGroup = group("category", "total", 10);
        GroupWrapper<UserRow> anotherMetric = group("status", "amount", 10);
        GroupWrapper<UserRow> anotherHaving = group("status", "total", 20);

        assertThat(fixture.execute(request(first), ParseType.count, loads::incrementAndGet)).isEqualTo(1);
        assertThat(fixture.execute(request(sameLogicalCount), ParseType.count, loads::incrementAndGet)).isEqualTo(1);
        assertThat(fixture.execute(request(anotherGroup), ParseType.count, loads::incrementAndGet)).isEqualTo(2);
        assertThat(fixture.execute(request(anotherMetric), ParseType.count, loads::incrementAndGet)).isEqualTo(3);
        assertThat(fixture.execute(request(anotherHaving), ParseType.count, loads::incrementAndGet)).isEqualTo(4);
        assertThat(loads).hasValue(4);
    }

    @Test
    void selectCacheStillKeepsPagesIsolated() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();

        SelectWrapper<UserRow> firstPage = select("users", 1).page(1, 20);
        SelectWrapper<UserRow> secondPage = select("users", 1).page(2, 20);

        assertThat(fixture.execute(request(firstPage), ParseType.select, loads::incrementAndGet)).isEqualTo(1);
        assertThat(fixture.execute(request(secondPage), ParseType.select, loads::incrementAndGet)).isEqualTo(2);
        assertThat(loads).hasValue(2);
        assertThat(fixture.cache.values).hasSize(2);
    }

    @Test
    void singleEqualitySelectUsesTheAutomaticPartitionScope() {
        Fixture fixture = new Fixture();
        SelectWrapper<UserRow> wrapper = select("users", 1);

        fixture.execute(request(wrapper), ParseType.select, () -> 1);

        assertThat(fixture.cache.partition).isNotNull();
        assertThat(fixture.cache.partition.field()).isEqualTo("status");
        assertThat(fixture.cache.partition.namespace().resource()).isEqualTo("users");
    }

    @Test
    void activeTransactionBypassesTheSharedQueryCache() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();
        RouteSession route = com.yulinlin.admin.support.TransactionTestSupport.route(fixture.session);
        route.startTransaction();
        route.session();
        try {
            assertThat(fixture.execute(CacheMode.READ_THROUGH,
                    () -> String.valueOf(loads.incrementAndGet()))).isEqualTo("1");
            assertThat(fixture.execute(CacheMode.READ_THROUGH,
                    () -> String.valueOf(loads.incrementAndGet()))).isEqualTo("2");
        } finally {
            route.rollbackTransaction();
        }

        assertThat(fixture.cache.gets).isZero();
        assertThat(fixture.cache.puts).isZero();
    }

    @Test
    void lockedSelectBypassesTheSharedQueryCache() {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();
        SelectWrapper<UserRow> wrapper = select("users", 1).lock();
        QueryRequest<UserRow> request = request(wrapper);

        assertThat(fixture.execute(request, ParseType.select, loads::incrementAndGet)).isEqualTo(1);
        assertThat(fixture.execute(request, ParseType.select, loads::incrementAndGet)).isEqualTo(2);

        assertThat(fixture.cache.gets).isZero();
        assertThat(fixture.cache.puts).isZero();
    }

    private static SelectWrapper<UserRow> select(String table, int status) {
        SelectWrapper<UserRow> wrapper = new SelectWrapper<>();
        wrapper.table(table);
        wrapper.where().eq("status", status);
        return wrapper;
    }

    private static GroupWrapper<UserRow> group(String aggregation, String metric, int having) {
        GroupWrapper<UserRow> wrapper = new GroupWrapper<>();
        wrapper.table("users");
        wrapper.where().eq("enabled", true);
        wrapper.aggregations().field(aggregation, aggregation);
        wrapper.metrics().sum(metric, "total");
        wrapper.having().gt("total", having);
        return wrapper;
    }

    private static QueryRequest<UserRow> request(com.yulinlin.data.core.node.INode wrapper) {
        return QueryRequest.newInstance(UserRow.class, wrapper).cache();
    }

    private static final class Fixture {
        final MemoryCache cache = new MemoryCache();
        final TestSession session = new TestSession();
        final CommandNode<Object> node = new CommandNode<>("select 1", Map.of(), ParseType.select);
        final ParseResult result = new ParseResult(ParseType.select, node, null);
        Fixture() {
            session.setCoderManager(new MapCoderManager());
            session.setQueryCache(cache);
        }
        String execute(CacheMode mode, Supplier<String> loader) {
            QueryRequest<String> request = QueryRequest.newInstance("select 1", Map.of(), String.class)
                    .cache(mode);
            return session.cached(request, result, loader);
        }

        <T> T execute(QueryRequest<?> request, ParseType type, Supplier<T> loader) {
            return session.cached(request, new ParseResult(type, request.getWrapper(), null), loader);
        }
    }

    private static final class MemoryCache implements QueryCache {
        final Map<CacheKey, Object> values = new HashMap<>();
        int gets;
        int puts;
        CachePartition partition;
        @Override public CacheKey scopePartition(CacheKey key, CachePartition partition, Duration ttl) {
            this.partition = partition;
            return key;
        }
        @Override public CacheLookup get(CacheKey key, CacheValueType valueType) {
            gets++;
            return values.containsKey(key) ? CacheLookup.hit(values.get(key)) : CacheLookup.miss();
        }
        @Override public void put(CacheKey key, CacheValueType valueType, Object value, Duration ttl) {
            puts++;
            values.put(key, value);
        }
    }

    private static final class TestSession extends AbstractSession {
        <T> T cached(QueryRequest<?> request, ParseResult result, Supplier<T> loader) {
            return getCacheValue(request, result, loader);
        }
        @Override protected Integer executeUpdate(List<ParseResult> list, RequestType requestType) { return 0; }
        @Override protected List<IDataBuffer> executeSelect(ParseResult request) { return List.of(); }
        @Override protected List<IDataBuffer> executeGroup(ParseResult request) { return List.of(); }
        @Override protected IDataBuffer executeCount(ParseResult request) { return null; }
        @Override public boolean ping() { return true; }
        @Override public void shutdown() { }
    }

    private record UserRow(long id, String name) {
    }
}

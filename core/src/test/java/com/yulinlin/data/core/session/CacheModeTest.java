package com.yulinlin.data.core.session;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheMissException;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.request.QueryRequest;
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

    private static final class Fixture {
        final MemoryCache cache = new MemoryCache();
        final TestSession session = new TestSession();
        final CommandNode<Object> node = new CommandNode<>("select 1", Map.of(), ParseType.select);
        final ParseResult result = new ParseResult(ParseType.select, node, null);
        Fixture() { session.setQueryCache(cache); }
        String execute(CacheMode mode, Supplier<String> loader) {
            QueryRequest<String> request = QueryRequest.newInstance("select 1", Map.of(), String.class)
                    .cache(mode);
            return session.cached(request, result, loader);
        }
    }

    private static final class MemoryCache implements QueryCache {
        final Map<CacheKey, Object> values = new HashMap<>();
        int gets;
        int puts;
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
}

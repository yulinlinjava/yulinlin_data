package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.cache.*;
import com.yulinlin.data.core.exception.ParseException;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.elasticsearch.coder.ElasticCoderManager;
import com.yulinlin.elasticsearch.enums.EsKeys;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import com.yulinlin.elasticsearch.session.ElasticsearchSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/** Real Java client and ORM execution pipeline, with an in-memory transport (no ES server). */
class EsCountTest {
    private final RecordingTransport transport = new RecordingTransport();
    private final ElasticsearchSession session = new ElasticsearchSession(new ElasticsearchClient(transport)) {
        @Override protected void printExecution(SessionExecution execution) { }
    };
    private final List<SessionExecution> executions = new ArrayList<>();

    EsCountTest() {
        session.setCoderManager(new ElasticCoderManager());
        session.setParseManager(new ElasticSearchParseManager());
        session.setProxyService(EntityProxyService.newInstance());
        session.setGroup("elasticsearch");
        session.setExecutionListeners(List.of(executions::add));
    }

    @AfterEach
    void close() {
        session.getThreadPoolExecutor().shutdownNow();
        transport.close();
    }

    @Test
    void indexHintsAreIgnoredWithoutChangingTheEsRequests() {
        var select = new SelectWrapper<>().table("people").page(2, 5);
        select.where().eq("status", "active");
        var request = request(select).useIndex("idx_people_name");
        transport.replies.add(response(9, 0));
        transport.replies.add(SearchResponse.of(b -> b.took(1).timedOut(false)
                .shards(s -> s.failed(0).successful(1).total(1)).hits(h -> h.hits(List.of()))));
        var page = session.page(request);
        assertEquals(9, page.getTotal());
        assertTrue(page.getList().isEmpty());
        assertEquals(List.of("/people/_count", "/people/_search"), transport.paths);
        var count = assertInstanceOf(CountRequest.class, transport.requests.getFirst());
        var search = assertInstanceOf(SearchRequest.class, transport.requests.getLast());
        assertEquals(List.of("people"), count.index());
        assertEquals(List.of("people"), search.index());
        assertEquals(parse(new CountWrapper(select), RequestType.count).getRequest().toString(), count.toString());
        assertEquals(parse(select, RequestType.select).getRequest().toString(), search.toString());
    }

    @Test
    void unsupportedExplainDoesNotFallBackToExecutingSearchOrCount() {
        var request = request(new SelectWrapper<>().table("people"));
        assertThrows(UnsupportedOperationException.class, () -> session.explain(request));
        assertThrows(UnsupportedOperationException.class, () -> session.explainCount(request));
        assertTrue(transport.requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 10001, Integer.MAX_VALUE})
    void countUsesNativeCountEndpointAndThePublicIntegerContract(long total) {
        transport.replies.add(response(total, 0));
        assertEquals((int) total, session.count(request(new SelectWrapper<>().table("people"))));
        assertEquals(List.of("/people/_count"), transport.paths);
        CountRequest sent = assertInstanceOf(CountRequest.class, transport.requests.getFirst());
        assertEquals(List.of("people"), sent.index());
        assertNull(sent.query(), "An absent predicate counts every document in the explicit index");
        assertNull(sent.terminateAfter(), "Never intentionally truncate a total");
        assertEquals(1, executions.size());
        assertNull(executions.getFirst().error());
        assertEquals(ParseType.count, executions.getFirst().requests().getFirst().getType());
    }

    @Test
    void countReusesMappedPredicatesButNeverMutatesSelectOptions() {
        var select = new SelectWrapper<>().table("people").page(4, 7).orderByDesc("displayName").lock();
        select.fields().field("displayName");
        select.highlight("displayName");
        select.where().eq("displayName", "Alice")
                .or(c -> c.eq("status", "active").eq("status", "pending"))
                .not(c -> c.eq("region", "blocked"));
        String before = parse(select, RequestType.select).getRequest().toString();
        ParseResult result = parse(new CountWrapper(select), RequestType.count);
        CountRequest counted = assertInstanceOf(CountRequest.class, result.getRequest());
        SearchRequest searched = (SearchRequest) parse(select, RequestType.select).getRequest();
        assertEquals(before, searched.toString());
        assertEquals(searched.query().toString(), counted.query().toString());
        assertEquals("display_name", counted.query().bool().must().getFirst().term().field());
        assertEquals(21, searched.from());
        assertEquals(7, searched.size());
        assertEquals(1, searched.sort().size());
        assertNotNull(searched.highlight());
        assertEquals(4, select.getPageNumber());
        assertTrue(select.isLock());
    }

    @Test
    void explicitFieldMetadataStillAppliesToCountPredicates() {
        var select = new SelectWrapper<>().table("people-*");
        select.where().meta(EsKeys.name, "profile.name.keyword").eq("displayName", "A\"lice");
        CountRequest counted = (CountRequest) parse(new CountWrapper(select), RequestType.count).getRequest();
        assertEquals(List.of("people-*"), counted.index());
        assertEquals("profile.name.keyword", counted.query().term().field());
        assertEquals("A\"lice", counted.query().term().value().stringValue());
    }

    @Test
    void explicitCountWrappersStillExecuteExactlyOneCountRequest() {
        var count = new CountWrapper(new CountWrapper(new SelectWrapper<>().table("people")));
        transport.replies.add(response(12, 0));
        assertEquals(12, session.count(request(count)));
        assertEquals(1, transport.requests.size());
    }

    @Test
    void missingIndexCannotAccidentallyCountTheEntireCluster() {
        assertThrows(ParseException.class, () -> session.count(request(new SelectWrapper<>())));
        assertThrows(ParseException.class, () -> session.count(request(new SelectWrapper<>().table("  "))));
        assertTrue(transport.requests.isEmpty());
    }

    @Test
    void unsupportedOrEmptyNodesFailBeforeTransport() {
        assertThrows(ParseException.class, () -> session.count(request(new CountWrapper(null))));
        var command = CommandNode.of("unsupported", Map.of(), ParseType.select);
        assertThrows(ParseException.class, () -> session.count(request(command)));
        assertTrue(transport.requests.isEmpty());
    }

    @Test
    void groupCountIsNotMisreportedAsADocumentCount() {
        var group = new GroupWrapper<>().table("people");
        group.aggregations().field("region", "region");
        group.metrics().count("id", "members");
        group.having().gt("members", 1);
        var error = assertThrows(UnsupportedOperationException.class, () -> session.count(request(group)));
        assertTrue(error.getMessage().contains("分组"));
        assertTrue(transport.requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {2147483648L, Long.MAX_VALUE})
    void oversizedCountIsRejectedWithoutIntegerTruncation(long total) {
        transport.replies.add(response(total, 0));
        var error = assertThrows(ArithmeticException.class,
                () -> session.count(request(new SelectWrapper<>().table("people"))));
        assertTrue(error.getMessage().contains("Integer"));
        assertSame(error, executions.getFirst().error());
    }

    @Test
    void invalidNegativeCountIsRejected() {
        transport.replies.add(response(-1, 0));
        assertThrows(IllegalStateException.class,
                () -> session.count(request(new SelectWrapper<>().table("people"))));
    }

    @Test
    void partialShardFailureDoesNotReturnOrCacheAPartialTotal() {
        var cache = new MemoryCache();
        session.setQueryCache(cache);
        var request = request(new SelectWrapper<>().table("people")).cache();
        transport.replies.add(response(7, 1));
        var error = assertThrows(IllegalStateException.class, () -> session.count(request));
        assertTrue(error.getMessage().contains("失败分片"));
        assertTrue(cache.values.isEmpty());
        assertSame(error, executions.getFirst().error());
        transport.replies.add(response(9, 0));
        assertEquals(9, session.count(request));
        assertEquals(1, cache.values.size());
    }

    @Test
    void reportedShardFailuresAreRejectedEvenIfTheFailureCounterIsInconsistent() {
        transport.replies.add(CountResponse.of(b -> b.count(7).shards(s -> s.failed(0).successful(1).total(1)
                .failures(f -> f.reason(r -> r.type("test_failure").reason("incomplete"))))));
        assertThrows(IllegalStateException.class,
                () -> session.count(request(new SelectWrapper<>().table("people"))));
    }

    @Test
    void transportAndServerFailuresPropagateInsteadOfBecomingZero() {
        var request = request(new SelectWrapper<>().table("people"));
        var io = new IOException("connection failed");
        transport.replies.add(io);
        assertSame(io, assertThrows(IOException.class, () -> session.count(request)));
        assertSame(io, executions.getFirst().error());
        var server = new ElasticsearchException("es/count", ErrorResponse.of(b -> b.status(404)
                .error(e -> e.type("index_not_found_exception").reason("missing"))));
        transport.replies.add(server);
        assertSame(server, assertThrows(ElasticsearchException.class, () -> session.count(request)));
        assertSame(server, executions.getLast().error());
    }

    @Test
    void countCacheRemainsIndependentOfPaginationOrderingAndProjection() {
        var cache = new MemoryCache();
        session.setQueryCache(cache);
        var select = new SelectWrapper<>().table("people").page(1, 2);
        select.where().eq("status", "active");
        var request = request(select).cache();
        transport.replies.add(response(15, 0));
        assertEquals(15, session.count(request));
        select.page(3, 4).orderByDesc("status").highlight("status");
        assertEquals(15, session.count(request));
        assertEquals(1, transport.requests.size());
        assertEquals(1, cache.values.size());
        assertEquals(1, executions.size(), "Cache hits do not emit backend execution events");
    }

    @Test
    void pageUsesTotalCountAndKeepsItsOriginalSearchAndHighlight() {
        var select = new SelectWrapper<>().table("people").page(3, 2).orderByDesc("status");
        select.fields().field("status");
        select.highlight("status");
        select.where().eq("status", "active");
        transport.replies.add(response(19, 0));
        transport.replies.add(SearchResponse.of(b -> b.took(1).timedOut(false)
                .shards(s -> s.failed(0).successful(1).total(1))
                .hits(h -> h.hits(hit -> hit.index("people").id("p1")
                        .highlight("status", List.of("<em>active</em>"))))));
        var page = session.page(request(select));
        assertEquals(19, page.getTotal());
        assertEquals(1, page.getList().size());
        assertEquals("p1", page.getList().getFirst().get("_id"));
        assertEquals("<em>active</em>", page.getList().getFirst().get("status"));
        assertEquals(List.of("/people/_count", "/people/_search"), transport.paths);
        SearchRequest search = assertInstanceOf(SearchRequest.class, transport.requests.get(1));
        assertEquals(4, search.from());
        assertEquals(2, search.size());
        assertEquals(1, search.sort().size());
        assertNotNull(search.highlight());
        assertEquals(((CountRequest) transport.requests.getFirst()).query().toString(), search.query().toString());
    }

    @Test
    void ordinaryGroupExecutionStillUsesItsExistingSearchPath() {
        var group = new GroupWrapper<>().table("people");
        group.metrics().sum("amount", "amountTotal");
        transport.replies.add(SearchResponse.of(b -> b.took(1).timedOut(false)
                .shards(s -> s.failed(0).successful(1).total(1)).hits(h -> h.hits(List.of()))
                .aggregations("amountTotal", a -> a.sum(s -> s.value(42.0)))));
        var rows = session.group(request(group));
        assertEquals(List.of("/people/_search"), transport.paths);
        assertEquals(42.0, rows.getFirst().get("amountTotal"));
        assertEquals(ParseType.group, executions.getFirst().requests().getFirst().getType());
    }

    private static QueryRequest<Map> request(INode node) {
        return QueryRequest.newInstance(Map.class, node);
    }

    private static ParseResult parse(INode node, RequestType type) {
        var params = new SimpParamsContext(type, Map.of(), new ElasticCoderManager().createEncoderBuffer(), Row.class, true);
        return (ParseResult) new ElasticSearchParseManager().parse(node, params);
    }

    private static CountResponse response(long total, int failed) {
        return CountResponse.of(b -> b.count(total).shards(s -> s.total(3).successful(3 - failed).failed(failed)));
    }

    public static class Row {
        @JoinField(name = "display_name") public String displayName;
        public String status;
        public String region;
    }

    private static final class RecordingTransport implements ElasticsearchTransport {
        final Deque<Object> replies = new ArrayDeque<>();
        final List<Object> requests = new ArrayList<>();
        final List<String> paths = new ArrayList<>();
        final JsonpMapper mapper = new JacksonJsonpMapper();

        @Override @SuppressWarnings("unchecked")
        public <R, S, E> S performRequest(R request, Endpoint<R, S, E> endpoint, TransportOptions options) throws IOException {
            requests.add(request);
            paths.add(endpoint.requestUrl(request));
            Object reply = replies.removeFirst();
            if (reply instanceof IOException error) throw error;
            if (reply instanceof RuntimeException error) throw error;
            return (S) reply;
        }

        @Override public <R, S, E> CompletableFuture<S> performRequestAsync(
                R request, Endpoint<R, S, E> endpoint, TransportOptions options) {
            throw new AssertionError("Synchronous count/page must not dispatch async requests");
        }
        @Override public JsonpMapper jsonpMapper() { return mapper; }
        @Override public TransportOptions options() { return DefaultTransportOptions.EMPTY; }
        @Override public void close() { }
    }

    private static final class MemoryCache implements QueryCache {
        final Map<CacheKey, Object> values = new HashMap<>();
        @Override public CacheLookup get(CacheKey key, CacheValueType type) {
            return values.containsKey(key) ? CacheLookup.hit(values.get(key)) : CacheLookup.miss();
        }
        @Override public void put(CacheKey key, CacheValueType type, Object value, Duration ttl) {
            values.put(key, value);
        }
    }
}

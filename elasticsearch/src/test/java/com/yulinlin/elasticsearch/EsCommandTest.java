package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;
import com.yulinlin.data.core.exception.ParseException;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.elasticsearch.coder.ElasticCoderManager;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import com.yulinlin.elasticsearch.parse.EsCommand;
import com.yulinlin.elasticsearch.session.ElasticsearchSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

/** 原生查询命令：CommandNode 的 JSON 经 EsCommandParse 解析，ElasticsearchSession 执行（内存 transport，无需 ES 服务）。 */
class EsCommandTest {
    private final RecordingTransport transport = new RecordingTransport();
    private final ElasticsearchSession session = new ElasticsearchSession(new ElasticsearchClient(transport)) {
        @Override protected void printExecution(SessionExecution execution) { }
    };

    EsCommandTest() {
        session.setCoderManager(new ElasticCoderManager());
        session.setParseManager(new ElasticSearchParseManager());
        session.setProxyService(EntityProxyService.newInstance());
        session.setGroup("elasticsearch");
    }

    @AfterEach void close() {
        session.getThreadPoolExecutor().shutdownNow();
        transport.close();
    }

    private static EsCommand parse(Object command, Map<String, Object> params, ParseType type) {
        var node = CommandNode.of(command, params, type);
        var context = new SimpParamsContext(RequestType.select, Map.of(), new ElasticCoderManager().createEncoderBuffer(), Map.class, true);
        return (EsCommand) ((ParseResult) new ElasticSearchParseManager().parse(node, context)).getRequest();
    }

    private static QueryRequest<Map> request(String command, Map<String, Object> params) {
        return QueryRequest.newInstance(Map.class, CommandNode.of(command, params, ParseType.select));
    }

    @Test void searchCommandBuildsTypedRequestWithParametersAsJsonValues() {
        var command = parse("""
                {"search": "orders-*, refunds", "size": 20, "_source": ["id", "status"],
                 "query": {"bool": {"filter": [{"term": {"status": "#{status}"}}, {"terms": {"id": "#{ids}"}},
                                                {"range": {"amount": {"gte": "#{min}"}}}]}},
                 "sort": [{"created_at": "desc"}]}""",
                Map.of("status", "paid", "ids", List.of("a1", "a2"), "min", 10.5), ParseType.select);
        assertEquals(EsCommand.Kind.SEARCH, command.kind());
        SearchRequest search = command.search();
        assertEquals(List.of("orders-*", "refunds"), search.index());
        assertEquals(20, search.size());
        String query = search.query().toString();
        assertTrue(query.contains("\"paid\""), query);
        assertTrue(query.contains("\"a1\"") && query.contains("\"a2\""), query);
        assertTrue(query.contains("10.5"), query);
    }

    @Test void parameterValuesCannotChangeTheQueryStructure() {
        var hostile = "\"}}, {\"match_all\": {}}, {\"term\": {\"x\": \"";
        var command = parse("{\"search\": \"orders\", \"query\": {\"term\": {\"status\": \"#{s}\"}}}", Map.of("s", hostile), ParseType.select);
        String query = command.search().query().toString();
        assertTrue(query.contains("term"), query);
        assertFalse(query.contains("\"match_all\":{}"), "parameter stays a single string value: " + query);
    }

    @Test void countAndAggregateCommands() {
        var count = parse(Map.of("count", "orders", "query", Map.of("match_all", Map.of())), null, ParseType.select);
        assertEquals(EsCommand.Kind.COUNT, count.kind());
        assertInstanceOf(CountRequest.class, count.count());
        var aggregate = parse("{\"aggregate\": \"orders\", \"size\": 0, \"aggs\": {\"amount\": {\"sum\": {\"field\": \"amount\"}}}}", Map.of(), ParseType.group);
        assertEquals(EsCommand.Kind.AGGREGATE, aggregate.kind());
        assertTrue(aggregate.search().aggregations().containsKey("amount"));
    }

    @Test void mappingCommandMayListAllIndicesButTakesNoBody() {
        var all = parse("{\"mapping\": \"*\"}", Map.of(), ParseType.select);
        assertEquals(EsCommand.Kind.MAPPING, all.kind());
        assertEquals(List.of("*"), all.indices());
        assertThrows(ParseException.class, () -> parse("{\"mapping\": \"orders\", \"query\": {}}", Map.of(), ParseType.select));
    }

    @Test void invalidCommandsFailBeforeTransport() {
        for (var bad : List.of("not json", "[1]", "{\"query\": {}}", "{\"search\": \"a\", \"count\": \"b\"}", "{\"search\": \"\"}",
                "{\"search\": \"*\"}", "{\"search\": \"a,_all\"}", "{\"search\": \"-secret\"}", "{\"search\": \"a\", \"query\": {\"nope\": {}}}"))
            assertThrows(ParseException.class, () -> parse(bad, Map.of(), ParseType.select), bad);
        assertThrows(ParseException.class, () -> parse("{\"search\": \"a\", \"query\": {\"term\": {\"x\": \"#{missing}\"}}}", Map.of(), ParseType.select));
        assertThrows(ParseException.class, () -> parse("{\"search\": \"a\"}", Map.of("unused", 1), ParseType.select));
        assertThrows(UnsupportedOperationException.class, () -> parse("{\"search\": \"a\"}", Map.of(), ParseType.update));
        assertTrue(transport.requests.isEmpty());
    }

    @Test void searchRowsContainSourceAndHitMetadata() {
        transport.replies.add(SearchResponse.<Map>of(b -> b.took(1).timedOut(false)
                .shards(s -> s.failed(0).successful(1).total(1))
                .hits(h -> h.hits(hit -> hit.index("orders").id("o1").score(1.5)
                        .source(Map.of("status", "paid", "items", List.of(Map.of("sku", "x"))))))));
        List<Map> rows = session.select(request("{\"search\": \"orders\", \"query\": {\"match_all\": {}}}", Map.of()));
        assertEquals(List.of("/orders/_search"), transport.paths);
        assertEquals(1, rows.size());
        assertEquals("paid", rows.getFirst().get("status"));
        assertEquals(List.of(Map.of("sku", "x")), rows.getFirst().get("items"));
        assertEquals("o1", rows.getFirst().get("_id"));
        assertEquals("orders", rows.getFirst().get("_index"));
    }

    @Test @SuppressWarnings("unchecked") void aggregateReturnsOneRowWithRawAggregations() {
        transport.replies.add(SearchResponse.<Map>of(b -> b.took(1).timedOut(false)
                .shards(s -> s.failed(0).successful(1).total(1))
                .hits(h -> h.total(t -> t.value(7).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations("amount", a -> a.sum(s -> s.value(42.0)))));
        List<Map> rows = session.select(request("{\"aggregate\": \"orders\", \"size\": 0, \"aggs\": {\"amount\": {\"sum\": {\"field\": \"amount\"}}}}", Map.of()));
        assertEquals(1, rows.size());
        assertEquals(7L, ((Number) rows.getFirst().get("total")).longValue());
        var aggregations = (Map<String, Object>) rows.getFirst().get("aggregations");
        assertEquals(42.0, ((Number) ((Map<String, Object>) aggregations.get("amount")).get("value")).doubleValue());
    }

    @Test void countReturnsOneRow() {
        transport.replies.add(CountResponse.of(b -> b.count(12).shards(s -> s.total(1).successful(1).failed(0))));
        List<Map> rows = session.select(request("{\"count\": \"orders\", \"query\": {\"term\": {\"status\": \"#{s}\"}}}", Map.of("s", "paid")));
        assertEquals(List.of("/orders/_count"), transport.paths);
        assertEquals(12L, ((Number) rows.getFirst().get("count")).longValue());
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

        @Override public <R, S, E> CompletableFuture<S> performRequestAsync(R request, Endpoint<R, S, E> endpoint, TransportOptions options) {
            throw new AssertionError("must not dispatch async requests");
        }
        @Override public JsonpMapper jsonpMapper() { return mapper; }
        @Override public TransportOptions options() { return DefaultTransportOptions.EMPTY; }
        @Override public void close() { }
    }
}

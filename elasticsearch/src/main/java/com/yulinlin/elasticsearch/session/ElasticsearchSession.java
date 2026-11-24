
package com.yulinlin.elasticsearch.session;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.AbstractSession;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.elasticsearch.log.EsExecutionLog;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.elasticsearch.util.AggregationUtil;
import com.yulinlin.elasticsearch.ElasticsearchSchemaManager;
import com.yulinlin.elasticsearch.parse.EsCommand;
import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.json.JsonpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;


@Slf4j
public class ElasticsearchSession extends AbstractSession  {

    @Override
    protected void printExecution(SessionExecution execution) {
        EsExecutionLog.print(execution, getSessionProperties().isLog());
    }

    private ElasticsearchClient client;
    private final ElasticsearchSchemaManager schemaManager = new ElasticsearchSchemaManager();

    public ElasticsearchSession(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public List<String> createTableSql(Class<?> entityClass) {
        return schemaManager.createIndexDefinition(entityClass, isMapUnderscoreToCamelCase());
    }

    @Override
    public void initializeSchema(Collection<Class<?>> entityClasses) {
        SchemaMode mode = getSessionProperties().getSchemaMode();
        schemaManager.initialize(client, entityClasses, isMapUnderscoreToCamelCase(), mode);
    }

    @SneakyThrows
    @Override
    protected IDataBuffer executeCount(ParseResult request) {
        if (!(request.getRequest() instanceof CountRequest body)) {
            throw new IllegalArgumentException("Elasticsearch count 需要 CountRequest");
        }
        CountResponse response = client.count(body);
        if (response.shards().failed().longValue() > 0 || !response.shards().failures().isEmpty()) {
            throw new IllegalStateException("Elasticsearch count 存在失败分片，拒绝返回不完整总数：failed="
                    + response.shards().failed());
        }
        long total = response.count();
        if (total < 0) {
            throw new IllegalStateException("Elasticsearch count 返回了无效的负数：" + total);
        }
        // The public ORM count/Page contract is Integer; never silently narrow an ES long.
        if (total > Integer.MAX_VALUE) {
            throw new ArithmeticException("Elasticsearch count 超出 Integer 范围：" + total);
        }
        IDataBuffer buffer = createDecoderBuffer();
        // AbstractSession.count reads the total as a string, as JDBC already does.
        buffer.put("total", Long.toString(total));
        return buffer;
    }

    @Override
    protected List<IDataBuffer> executeGroup(ParseResult request) {

        return executeSelect(request);
    }

    @SneakyThrows
    @Override
    protected Integer executeUpdate(List<ParseResult> list,RequestType requestType) {
        int total = 0;
        for (ParseResult result : list) {
            switch (requestType){
                case insert:{
                    IndexRequest req = (IndexRequest)result.getRequest();

                    IndexResponse resp = client.index(req);
                    total+=  resp.shards().successful().intValue();
                    break;
                }case update:{
                    UpdateByQueryRequest req = (UpdateByQueryRequest)result.getRequest();

                    UpdateByQueryResponse resp = client.updateByQuery(req);
                    total+=  resp.updated();
                    break;
                }case delete:{
                    DeleteByQueryRequest req = (DeleteByQueryRequest)result.getRequest();
                    DeleteByQueryResponse resp = client.deleteByQuery(req);
                    total+=resp.deleted();
                    break;
                }
            }

        }


        return total;
    }

    @SneakyThrows
    @Override
    protected List<IDataBuffer> executeSelect(ParseResult request) {

        if (request.getRequest() instanceof EsCommand command) return executeCommand(command);

        SearchRequest body =  (SearchRequest)     request.getRequest();
        SearchResponse response = null;


            response = client.search(body,Map.class);


        List<IDataBuffer> iDataBuffers;
        if(request.getType() == ParseType.group){

            iDataBuffers =  AggregationUtil.toBufferList(response.aggregations(),createDecoderBuffer());

        }else {

            List<Hit> hits = response.hits().hits();
            iDataBuffers = AggregationUtil.toBufferList(hits, getCoderManager(),
                    getSessionProperties().getHighlight().getFragmentDelimiter());
        }


        return iDataBuffers;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 原生查询命令（见 EsCommandParse）：search 每条命中一行；aggregate 一行 {total, aggregations}；count 一行 {count}；mapping 每个索引一行 {index, mappings}。 */
    @SneakyThrows
    private List<IDataBuffer> executeCommand(EsCommand command) {
        switch (command.kind()) {
            case COUNT: {
                CountResponse response = client.count(command.count());
                if (response.shards().failed().longValue() > 0 || !response.shards().failures().isEmpty())
                    throw new IllegalStateException("Elasticsearch count 存在失败分片，拒绝返回不完整总数：failed=" + response.shards().failed());
                IDataBuffer buffer = createDecoderBuffer();
                buffer.put("count", response.count());
                return List.of(buffer);
            }
            case AGGREGATE: {
                SearchResponse<Map> response = client.search(command.search(), Map.class);
                IDataBuffer buffer = createDecoderBuffer();
                var total = response.hits().total();
                buffer.put("total", total == null ? null : total.value());
                var aggregations = new LinkedHashMap<String, Object>();
                response.aggregations().forEach((name, aggregate) -> aggregations.put(name, toJava(aggregate)));
                buffer.put("aggregations", aggregations);
                return List.of(buffer);
            }
            case MAPPING: {
                var response = client.indices().getMapping(g -> g.index(command.indices()));

                var rows = new java.util.ArrayList<IDataBuffer>();
                // 9.x 客户端：GetMappingResponse.mappings() 返回 {索引名: IndexMappingRecord}（8.x 为 result()）
                new java.util.TreeMap<>(response.mappings()).forEach((index, record) -> {
                    IDataBuffer buffer = createDecoderBuffer();
                    buffer.put("index", index);
                    buffer.put("mappings", toJava(record.mappings()));
                    rows.add(buffer);
                });
                return rows;
            }
            default: {
                SearchResponse<Map> response = client.search(command.search(), Map.class);
                var rows = new java.util.ArrayList<IDataBuffer>();
                for (Hit<Map> hit : response.hits().hits()) {
                    IDataBuffer buffer = createDecoderBuffer();
                    if (hit.source() != null) hit.source().forEach((k, v) -> buffer.put(String.valueOf(k), v));
                    hit.fields().forEach((k, v) -> buffer.put(k, parseJson(v.toJson(client._jsonpMapper()).toString())));
                    hit.highlight().forEach((k, v) -> buffer.put(k, String.join(getSessionProperties().getHighlight().getFragmentDelimiter(), v)));
                    buffer.put("_id", hit.id());
                    buffer.put("_index", hit.index());
                    buffer.put("_score", hit.score());
                    rows.add(buffer);
                }
                return rows;
            }
        }
    }

    private Object toJava(JsonpSerializable value) {
        return parseJson(JsonpUtils.toJsonString(value, client._jsonpMapper()));
    }

    @SneakyThrows
    private static Object parseJson(String json) {
        return JSON.readValue(json, Object.class);
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public void shutdown() {

    }

}



package com.yulinlin.elasticsearch.session;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.AbstractSession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.elasticsearch.util.AggregationUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;


@Slf4j
public class ElasticsearchSession extends AbstractSession  {

    private ElasticsearchClient client;

    public ElasticsearchSession(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    protected IDataBuffer executeCount(ParseResult request) {
        return null;
    }

    @Override
    protected List<IDataBuffer> executeGroup(ParseResult request) {

        return executeSelect(request);
    }

    @Override
    protected boolean isMapUnderscoreToCamelCase() {
        return true;
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

        SearchRequest body =  (SearchRequest)     request.getRequest();
        SearchResponse response = null;


            response = client.search(body,Map.class);


        List<IDataBuffer> iDataBuffers;
        if(request.getType() == ParseType.group){

            iDataBuffers =  AggregationUtil.toBufferList(response.aggregations(),createDecoderBuffer());

        }else {

            List<Hit> hits = response.hits().hits();
            iDataBuffers = AggregationUtil.toBufferList(hits, getCoderManager());
        }


        return iDataBuffers;
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void startTransaction() {

    }

    @Override
    public void commitTransaction() {

    }

    @Override
    public void rollbackTransaction() {

    }

}


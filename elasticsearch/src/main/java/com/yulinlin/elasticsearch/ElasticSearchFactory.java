
package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCaches;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.SessionFactory;
import com.yulinlin.elasticsearch.coder.ElasticCoderManager;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import com.yulinlin.elasticsearch.session.ElasticsearchSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;


public class ElasticSearchFactory implements SessionFactory<ElasticsearchClient> {


    @Autowired
    EntityProxyService entityProxyService;


@Autowired
   private LogManager logManager;



    private ICoderManager coderManager = new ElasticCoderManager();
    @Autowired
    ElasticSearchProperties properties;

    private ElasticSearchParseManager parseManager ;

    private QueryCache queryCache = NoOpQueryCache.INSTANCE;


    public ElasticSearchFactory(ElasticSearchParseManager parseManager) {
        this.parseManager = parseManager;

    }

    @Autowired
    void setQueryCaches(ObjectProvider<QueryCache> providers) {
        this.queryCache = QueryCaches.single(providers.orderedStream().toList());
    }

    public ElasticsearchSession create(ElasticsearchClient restClient, String group){

        ElasticsearchSession searchSession =  new ElasticsearchSession(restClient);
        searchSession.setQueryCache(queryCache);
        searchSession.setSessionProperties(properties);
        searchSession.setParseManager(parseManager);

        searchSession.setCoderManager(coderManager);


        searchSession.setLogManager(logManager);

        searchSession.setGroup(group);
        return searchSession;
    }
}


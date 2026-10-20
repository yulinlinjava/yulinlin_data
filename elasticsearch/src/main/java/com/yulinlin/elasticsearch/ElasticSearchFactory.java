
package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.CacheRegistry;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.session.SessionExecutionListener;
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


    private java.util.List<SessionExecutionListener> executionListeners = java.util.List.of();

    @Autowired
    void setExecutionListeners(ObjectProvider<SessionExecutionListener> listeners) {
        this.executionListeners = listeners.orderedStream().toList();
    }



    private ICoderManager coderManager = new ElasticCoderManager();
    @Autowired
    ElasticSearchProperties properties;

    private ElasticSearchParseManager parseManager ;

    private QueryCache queryCache = NoOpQueryCache.INSTANCE;


    public ElasticSearchFactory(ElasticSearchParseManager parseManager) {
        this.parseManager = parseManager;

    }

    @Autowired
    void setQueryCaches(CacheRegistry caches) {
        this.queryCache = caches.defaultCache();
    }

    public ElasticsearchSession create(ElasticsearchClient restClient, String group){

        ElasticsearchSession searchSession =  new ElasticsearchSession(restClient);
        searchSession.setQueryCache(queryCache);
        searchSession.setSessionProperties(properties);
        searchSession.setParseManager(parseManager);

        searchSession.setCoderManager(coderManager);
        searchSession.setProxyService(entityProxyService);


        searchSession.setExecutionListeners(executionListeners);

        searchSession.setGroup(group);
        return searchSession;
    }
}


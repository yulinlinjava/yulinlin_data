
package com.yulinlin.mongodb;

import com.mongodb.client.MongoDatabase;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.CacheRegistry;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.session.SessionExecutionListener;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.SessionFactory;
import com.yulinlin.mongodb.coder.MongoCoderManager;
import com.yulinlin.mongodb.parse.MongoParseManager;
import com.yulinlin.mongodb.session.MongoSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;



public class MongoFactory implements SessionFactory<MongoDatabase> {



    private java.util.List<SessionExecutionListener> executionListeners = java.util.List.of();

    @Autowired
    void setExecutionListeners(ObjectProvider<SessionExecutionListener> listeners) {
        this.executionListeners = listeners.orderedStream().toList();
    }

    @Autowired
    private EntityProxyService entityProxyService;



    private ICoderManager coderManager = new MongoCoderManager();

    @Autowired
    MongoProperties properties;

    private MongoParseManager parseManager;

    private QueryCache queryCache = NoOpQueryCache.INSTANCE;



    public MongoFactory(MongoParseManager parseManager) {
        this.parseManager = parseManager;

    }

    @Autowired
    void setQueryCaches(CacheRegistry caches) {
        this.queryCache = caches.defaultCache();
    }

    public MongoSession create(MongoDatabase restClient, String group){

        MongoSession searchSession =  new MongoSession(restClient);
        searchSession.setQueryCache(queryCache);
        searchSession.setSessionProperties(properties);

        searchSession.setCoderManager(coderManager);
        searchSession.setProxyService(entityProxyService);
        searchSession.setParseManager(parseManager);

        searchSession.setExecutionListeners(executionListeners);

        searchSession.setGroup(group);
        return searchSession;
    }
}


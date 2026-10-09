
package com.yulinlin.mongodb;

import com.mongodb.client.MongoDatabase;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCaches;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.session.SessionFactory;
import com.yulinlin.mongodb.coder.MongoCoderManager;
import com.yulinlin.mongodb.parse.MongoParseManager;
import com.yulinlin.mongodb.session.MongoSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;



public class MongoFactory implements SessionFactory<MongoDatabase> {



@Autowired
   private LogManager logManager;



    private ICoderManager coderManager = new MongoCoderManager();

    @Autowired
    MongoProperties properties;

    private MongoParseManager parseManager;

    private QueryCache queryCache = NoOpQueryCache.INSTANCE;



    public MongoFactory(MongoParseManager parseManager) {
        this.parseManager = parseManager;

    }

    @Autowired
    void setQueryCaches(ObjectProvider<QueryCache> providers) {
        this.queryCache = QueryCaches.single(providers.orderedStream().toList());
    }

    public MongoSession create(MongoDatabase restClient, String group){

        MongoSession searchSession =  new MongoSession(restClient);
        searchSession.setQueryCache(queryCache);
        searchSession.setSessionProperties(properties);

        searchSession.setCoderManager(coderManager);
        searchSession.setParseManager(parseManager);

        searchSession.setLogManager(logManager);

        searchSession.setGroup(group);
        return searchSession;
    }
}



package com.yulinlin.mongodb;

import com.mongodb.client.MongoDatabase;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.session.SessionFactory;
import com.yulinlin.mongodb.coder.MongoCoderManager;
import com.yulinlin.mongodb.parse.MongoParseManager;
import com.yulinlin.mongodb.session.MongoSession;
import org.springframework.beans.factory.annotation.Autowired;



public class MongoFactory implements SessionFactory<MongoDatabase> {



@Autowired
   private LogManager logManager;



    private ICoderManager coderManager = new MongoCoderManager();

    @Autowired
    MongoProperties properties;

    private MongoParseManager parseManager;



    public MongoFactory(MongoParseManager parseManager) {
        this.parseManager = parseManager;

    }

    public MongoSession create(MongoDatabase restClient, String group){

        MongoSession searchSession =  new MongoSession(restClient);

        searchSession.setCoderManager(coderManager);
        searchSession.setParseManager(parseManager);

        searchSession.setLogManager(logManager);

        searchSession.setGroup(group);
        return searchSession;
    }
}


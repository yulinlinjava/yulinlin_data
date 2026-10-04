package com.yulinlin.mongodb.log;

import com.yulinlin.data.core.log.LogPrint;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.mongodb.MongoProperties;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class MongoLogPrint implements LogPrint<BsonDocument> {



    @Autowired
    MongoProperties properties;


    @Override
    public void success(long time, ParseResult request) {
        if(!properties.isLog()){
            return;
        }
        EntitySession session =  SessionUtil.route().session();

        ParseType type = request.getType();



        String sql =((BsonDocument)request.getRequest()).toJson();


        log.info("[{}][{}]" +
                        "[{}]" +
                        "[{}]\n" +
                        "[{}]",
                session.group(),
                session.cluster(),
                time,
                type,
                sql);
    }

    @Override
    public void error(Throwable e, ParseResult request) {
        String sql =((BsonDocument)request.getRequest()).toJson();

        EntitySession session =  SessionUtil.route().session();
        ParseType type = request.getType();
        log.info("[{}][{}]" +

                        "[{}]\n" +
                        "[{}]",
                session.group(),
                session.cluster(),

                type,
                sql);
    }

}

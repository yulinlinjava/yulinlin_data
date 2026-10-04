
package com.yulinlin.mongodb.parse.wrapper;


import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.*;

import java.util.Map;

public class MongoGroupWrapperParse implements IParse<GroupWrapper> {



    @Override
    public Object parse(GroupWrapper condition, IParamsContext params, IParseManager parseManager) {




        BsonDocument where =(BsonDocument) parseManager.parse(condition.where(),params);

        AliasUtil.set(false);
        BsonDocument having = null;
        try {
             having =(BsonDocument) parseManager.parse(condition.having(),params);
        }finally {
            AliasUtil.set(true);
        }



        BsonDocument[] aggregationsList =(BsonDocument[]) parseManager.parse(condition.aggregations(),params);
        BsonDocument[] metricsList =(BsonDocument[]) parseManager.parse(condition.metrics(),params);

        BsonDocument project = aggregationsList[0];
        BsonDocument aggregations =aggregationsList[1];
        if(!metricsList[0].isEmpty()){
            project.putAll(metricsList[0]);
        }

        BsonDocument metrics =metricsList[1];

        //BsonDocument project = project(aggregations,metrics);

        BsonDocument _id = new BsonDocument();
        for (Map.Entry<String, BsonValue> entry : aggregations.entrySet()) {
            _id.append(entry.getKey(),entry.getValue());
        }

        String index = (String) parseManager.parse(condition.getFrom(),params);

        BsonDocument group = new BsonDocument();

        group.append("_id",_id);
        for (Map.Entry<String, BsonValue> entry : metrics.entrySet()) {
            group.append(entry.getKey(),entry.getValue());
        }
        BsonArray pipeline = new BsonArray();

        if(where != null && where.size() > 0){
            pipeline.add(new BsonDocument().append("$match",where));
        }

       pipeline
                .add(new BsonDocument().append("$project",project));

        pipeline .add(new BsonDocument().append("$group",group));

        if(having != null && having.size() > 0){
            pipeline.add(new BsonDocument().append("$match",having));
        }

        BsonDocument document =  new BsonDocument()

                .append("aggregate",BsonUtil.toBsonValue(index))
                .append("pipeline",pipeline)
                .append("cursor",new BsonDocument())
                ;


        return new ParseResult(ParseType.group,document,params);
    }
}


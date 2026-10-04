package com.yulinlin.mongodb.parse.predicate;

import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.stream.Collectors;

public class AndParse implements IParse<And> {

    @Override
    public Object parse(And condition, IParamsContext params, IParseManager parseManager) {

        List<Bson> collect =(List) condition.getList().stream().map(row -> parseManager.parse(row, params))

                .filter(row -> row != null)
                .collect(Collectors.toList());

        if(collect.size() == 0){
            return null;
        }else if(collect.size() == 1){
            return collect.get(0);
        }




        return Filters.and(collect).toBsonDocument();


   
    }
}

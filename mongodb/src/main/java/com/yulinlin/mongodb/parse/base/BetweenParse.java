package com.yulinlin.mongodb.parse.base;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.base.Between;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.conversions.Bson;

public class BetweenParse implements IParse<Between> {

    @Override
    public Object parse(Between condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        Object[] value =  condition.getValue().toArray();


        Bson gte = Filters.gte(key,
                params.encode(
                    value[0]
                )
        );
        Bson lt = Filters.lt(key,
                params.encode(
                        value[1]
                )
        );

        return Filters.and(gte,lt).toBsonDocument();
    }

}

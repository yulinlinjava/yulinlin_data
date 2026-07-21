package com.yulinlin.mongodb.parse.base;


import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.base.Gte;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.*;

public class GteParse implements IParse<Gte> {

    @Override
    public Object parse(Gte condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);

        Object encode = params.encode(condition.getValue());


        return Filters.gte(key,encode).toBsonDocument();


    }
}

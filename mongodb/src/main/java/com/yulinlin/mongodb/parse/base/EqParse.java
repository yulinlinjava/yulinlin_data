package com.yulinlin.mongodb.parse.base;

import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;

import java.util.HashMap;

public class EqParse implements IParse<Eq> {

    @Override
    public Object parse(Eq condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        Object encode = params.encode(condition.getValue());


        return Filters.eq(key,encode).toBsonDocument();


    }
}

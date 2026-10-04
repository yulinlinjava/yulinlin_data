package com.yulinlin.mongodb.parse.base;

import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.base.Lt;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;

public class LtParse implements IParse<Lt> {

    @Override
    public Object parse(Lt condition, IParamsContext params, IParseManager parseManager) {


        String key =AliasUtil.parse(condition,params);

        Object encode = params.encode(condition.getValue());

        return         Filters.lt(key,encode).toBsonDocument();







    }
}

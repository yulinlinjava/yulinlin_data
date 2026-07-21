package com.yulinlin.mongodb.parse.base;

import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.base.IsNull;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;

public class IsNullParse implements IParse<IsNull> {

    @Override
    public BsonDocument parse(IsNull condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        Bson exists = Filters.exists(key);
        return Filters.not(exists).toBsonDocument();
    }
}

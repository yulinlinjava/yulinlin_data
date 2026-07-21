package com.yulinlin.mongodb.parse.script;

import com.yulinlin.data.core.node.metrics.MaxMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;
import org.bson.BsonString;

public class MaxFieldParse implements IParse<MaxMetrics> {

    @Override
    public Object parse(MaxMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        return   new BsonDocument().append("$max",BsonUtil.toBsonKey(key));
    }

}

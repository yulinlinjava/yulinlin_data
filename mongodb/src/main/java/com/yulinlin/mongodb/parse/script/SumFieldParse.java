package com.yulinlin.mongodb.parse.script;

import com.yulinlin.data.core.node.metrics.SumMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;
import org.bson.BsonString;

public class SumFieldParse implements IParse<SumMetrics> {

    @Override
    public Object parse(SumMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        return   new BsonDocument().append("$sum",BsonUtil.toBsonKey(key));
    }

}

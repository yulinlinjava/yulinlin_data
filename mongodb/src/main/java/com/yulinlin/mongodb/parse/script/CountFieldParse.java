package com.yulinlin.mongodb.parse.script;

import com.yulinlin.data.core.node.metrics.CountMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

public class CountFieldParse implements IParse<CountMetrics> {

    @Override
    public Object parse(CountMetrics condition, IParamsContext params, IParseManager parseManager) {

        return   new BsonDocument().append("$sum",new BsonInt32(1));
    }

}

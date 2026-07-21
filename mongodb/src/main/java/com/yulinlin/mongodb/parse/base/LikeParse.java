package com.yulinlin.mongodb.parse.base;

import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.base.Like;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;

import java.util.regex.Pattern;

public class LikeParse implements IParse<Like> {

    @Override
    public Object parse(Like condition, IParamsContext params, IParseManager parseManager) {



        String key =AliasUtil.parse(condition,params);
        String value =  (String)params.encode( condition.getValue());



        return         Filters.regex(key,value).toBsonDocument();
    }
}

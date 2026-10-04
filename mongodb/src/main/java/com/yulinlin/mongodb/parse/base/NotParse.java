package com.yulinlin.mongodb.parse.base;


import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

import org.bson.BsonDocument;


public class NotParse implements IParse<Not> {

    @Override
    public Object parse(Not condition, IParamsContext params, IParseManager parseManager) {

        And and = new And(condition.getList());

        BsonDocument parse = (BsonDocument)parseManager.parse(and, params);

        return Filters.not(parse).toBsonDocument();




    }
}

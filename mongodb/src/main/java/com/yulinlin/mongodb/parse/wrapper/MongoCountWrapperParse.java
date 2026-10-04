package com.yulinlin.mongodb.parse.wrapper;

import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import org.bson.BsonDocument;

public class MongoCountWrapperParse implements IParse<CountWrapper> {



    @Override
    public ParseResult parse(CountWrapper condition, IParamsContext params, IParseManager parseManager) {


        ParseResult select = (ParseResult)parseManager.parse(condition.getWrapper(), params);

        BsonDocument request = (BsonDocument)select.getRequest();

        BsonDocument document = new BsonDocument();
        document.append("count",request.getString("find"));
        document.append("query",request.getDocument("filter"));



        return new ParseResult(ParseType.count,document,params);


    }
}

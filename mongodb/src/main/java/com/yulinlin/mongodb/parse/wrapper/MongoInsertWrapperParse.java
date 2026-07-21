package com.yulinlin.mongodb.parse.wrapper;

import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

public class MongoInsertWrapperParse implements IParse<InsertWrapper> {

    @Override
    public Object parse(InsertWrapper condition, IParamsContext params, IParseManager parseManager) {
        String index = (String) parseManager.parse(condition.getFrom(),params);
        BsonValue bsonValue =(BsonValue) parseManager.parse(condition.fields(), params);


        BsonArray documents = new BsonArray();

        documents.add(bsonValue);
        BsonDocument document =  new BsonDocument()
                .append("insert",BsonUtil.toBsonValue(index))
                .append("documents",documents)
                ;


        return new ParseResult(ParseType.insert,document,params);



    }
}

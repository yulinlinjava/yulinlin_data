package com.yulinlin.mongodb.parse.wrapper;

import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.DeleteWrapper;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

public class MongoDeleteWrapperParse implements IParse<DeleteWrapper> {

    @Override
    public Object parse(DeleteWrapper condition, IParamsContext params, IParseManager parseManager) {
        String index = (String) parseManager.parse(condition.getFrom(),params);

        BsonValue query = (BsonValue)parseManager.parse(condition.where(),params);

        if(query == null){
            query = new BsonDocument();
        }
        BsonArray bsonValues = new BsonArray();

            bsonValues.add(new BsonDocument("q",query).append("limit",BsonUtil.toBsonValue(0)));



        BsonDocument document =  new BsonDocument()
                .append("delete",BsonUtil.toBsonValue(index))
                .append("deletes",bsonValues)
                ;

        return new ParseResult(ParseType.delete,document,params);


    }
}

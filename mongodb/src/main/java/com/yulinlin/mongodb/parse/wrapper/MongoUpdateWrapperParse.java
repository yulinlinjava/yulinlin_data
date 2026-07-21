package com.yulinlin.mongodb.parse.wrapper;


import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

public class MongoUpdateWrapperParse implements IParse<UpdateWrapper> {




        @Override
    public Object parse(UpdateWrapper condition, IParamsContext params, IParseManager parseManager) {

                BsonDocument set  =(BsonDocument) parseManager.parse(condition.fields(), params);

            String index = (String) parseManager.parse(condition.getFrom(),params);
            BsonValue where = (BsonValue) parseManager.parse(condition.where(),params);

            BsonDocument update = new BsonDocument();
                update.append("q",where);
                update.append("u",set);
                update.append("multi",BsonUtil.toBsonValue(true));
                BsonArray updates = new BsonArray();

                updates.add(update);

            BsonDocument document =  new BsonDocument()
                    .append("update",BsonUtil.toBsonValue(index))
                    .append("updates",updates)


                    ;


            return new ParseResult(ParseType.update,document,params);
    }
}

package com.yulinlin.mongodb.parse.wrapper;

import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;
import org.bson.BsonValue;

public class MongoSelectWrapperParse implements IParse<SelectWrapper> {



    @Override
    public ParseResult parse(SelectWrapper condition, IParamsContext params, IParseManager parseManager) {


        BsonValue filter = (BsonValue)parseManager.parse(condition.where(),params);
        BsonValue sort =(BsonValue) parseManager.parse(condition.getOrder(),params);
        BsonValue projection =(BsonValue) parseManager.parse(condition.fields(),params);
        String index = (String) parseManager.parse(condition.getFrom(),params);

        if(filter == null){
            filter = new BsonDocument();
        }

        BsonDocument document =  new BsonDocument()
                .append("find",BsonUtil.toBsonValue(index))
                .append("projection",projection)
                ;
        if(filter != null){
            document.append("filter",filter);
        }

        if(sort != null){
            document.append("sort",sort);
        }
        if(condition.getPageNumber() > 0 && condition.getPageSize() > 0){
            int start = (condition.getPageNumber() - 1) * condition.getPageSize();
            document.append("skip",BsonUtil.toBsonValue(start));
            document.append("limit",BsonUtil.toBsonValue(condition.getPageSize()));
        }

        return new ParseResult(ParseType.select,document,params);


    }
}

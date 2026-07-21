package com.yulinlin.mongodb.parse.group;

import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;
import org.bson.BsonString;

import java.util.HashMap;
import java.util.Map;

public class DateParse implements IParse<DateGroup> {



    private static Map<DateGroup.Type,String> funcMap;

    //%Y/%m/%d %H:%i:00
    static {
        //周期的允许重复，其他的不允许
        funcMap = new HashMap<>();
        funcMap.put(DateGroup.Type.minute,"%Y-%m-%d %H:%M:00");
        funcMap.put(DateGroup.Type.hour,"%Y-%m-%d %H");
        funcMap.put(DateGroup.Type.day,"%Y-%m-%d");
        funcMap.put(DateGroup.Type.month,"%Y-%m)");
        funcMap.put(DateGroup.Type.quarter,"%Y-%m");
        funcMap.put(DateGroup.Type.year,"%Y");
    }

    

    @Override
    public Object parse(DateGroup condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);

        DateGroup.Type dateType = condition.getDateType();

        String format =funcMap.get(dateType);

        BsonDocument document = new BsonDocument().append("$dateToString",
                new BsonDocument()
                        .append("format", new BsonString(format))
                        .append("date", BsonUtil.toBsonKey(key))
        );

        return document;
    }


}

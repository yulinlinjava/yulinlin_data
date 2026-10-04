package com.yulinlin.elasticsearch.parse.group;

import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

import java.util.HashMap;
import java.util.Map;

public class DateParse implements IParse<DateGroup> {

     static Map< DateGroup.Type,String> funcMap;

    //%Y/%m/%d %H:%i:00
    static {
        //周期的允许重复，其他的不允许
        funcMap = new HashMap<>();
        funcMap.put( DateGroup.Type.minute,"yyyy-MM-dd HH:mm:00");
        funcMap.put( DateGroup.Type.hour,"yyyy-MM-dd HH:00:00");
        funcMap.put( DateGroup.Type.day,"yyyy-MM-dd");
        funcMap.put( DateGroup.Type.month,"yyyy-MM");
        funcMap.put( DateGroup.Type.quarter,"yyyy-MM");
        funcMap.put( DateGroup.Type.year,"yyyy");
    }



    @Override
    public Object parse(DateGroup condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);

        DateGroup.Type dateType = condition.getDateType();

     return GroupUtil.get().dateHistogram(
             f -> f.field(key)
                     .interval(t -> {
                         return    t.time(dateType.name());
                     })
                     .format(funcMap.get(dateType.name())

     )
               );

    }


}

package com.yulinlin.jdbc.postgresql.parse.group;

import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

import java.util.HashMap;
import java.util.Map;

public class DateParse implements IParse<DateGroup> {

    private static Map<DateGroup.Type,String> funcMap;

    //%Y/%m/%d %H:%i:00
    static {
        //周期的允许重复，其他的不允许
        funcMap = new HashMap<>();
        funcMap.put(DateGroup.Type.minute,"DATE_FORMAT(${columnName},'%Y-%m-%d %H:%i:00')");
        funcMap.put(DateGroup.Type.hour,"DATE_FORMAT(${columnName},'%Y-%m-%d %H')");
        funcMap.put(DateGroup.Type.day,"DATE_FORMAT(${columnName},'%Y-%m-%d')");
        funcMap.put(DateGroup.Type.month,"DATE_FORMAT(${columnName},'%Y-%m')");
        funcMap.put(DateGroup.Type.quarter,"DATE_FORMAT(${columnName},CONCAT('%Y-%m-', FLOOR(MONTH(${columnName})/ 3  + 1)  * 3 ))");

        funcMap.put(DateGroup.Type.year,"DATE_FORMAT(${columnName},'%Y')");
    }



    @Override
    public Object parse(DateGroup condition, IParamsContext params, IParseManager parseManager) {
        String func =  funcMap.get(condition.getDateType());
        String key = AliasUtil.parse(condition,params) ;
        func = func.replace("${columnName}",key);

        return func;

    }


}

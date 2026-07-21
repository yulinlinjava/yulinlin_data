package com.yulinlin.jdbc.mysql.parse.group;

import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class IntervalParse implements IParse<IntervalGroup> {


    String func="(FLOOR(${columnName}/${cycle}) + 1 )* ${cycle}";

    @Override
    public Object parse(IntervalGroup condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;
           return func.replace("${columnName}",key).replace("${cycle}",""+condition.getInterval());

    }


}

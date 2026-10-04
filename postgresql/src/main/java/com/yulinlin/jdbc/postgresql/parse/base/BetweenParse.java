package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.Between;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

import java.util.Collection;

public class BetweenParse implements IParse<Between> {

    @Override
    public String parse(Between condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;
        Object value = condition.getValue();
        Object[] array = null;
        if(value instanceof Collection){
            Collection collection = (Collection)value;
            array = collection.toArray();
        }else if(value.getClass().isArray()){
            array = (Object[]) value;
        }
       String x =  params.putGetKey(array[0]);
        String y =  params.putGetKey(array[1]);

        String sql =
                key+" between " +
                x+
                " and " +y;
        return sql;
    }
}

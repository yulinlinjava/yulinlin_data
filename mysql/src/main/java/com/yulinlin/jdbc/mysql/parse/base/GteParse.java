package com.yulinlin.jdbc.mysql.parse.base;

import com.yulinlin.data.core.node.base.Gte;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class GteParse implements IParse<Gte> {

    @Override
    public String parse(Gte condition, IParamsContext params, IParseManager parseManager) {

        String key = AliasUtil.parse(condition,params) ;

        Object value = params.putGetKey(condition.getValue());


        String sql =key+" >= " + value;


        return sql;
    }
}

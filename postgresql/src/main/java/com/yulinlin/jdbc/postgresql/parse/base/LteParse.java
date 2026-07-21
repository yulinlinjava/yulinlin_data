package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.Lte;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

public class LteParse implements IParse<Lte> {

    @Override
    public String parse(Lte condition, IParamsContext params, IParseManager parseManager) {


        String key = AliasUtil.parse(condition,params) ;

        Object value = params.putGetKey(condition.getValue());
        String sql =key+" <= " + value;


        return sql;
    }
}

package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.Gt;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

public class GtParse implements IParse<Gt> {

    @Override
    public String parse(Gt condition, IParamsContext params, IParseManager parseManager) {

        String key = AliasUtil.parse(condition,params) ;

        Object value = params.putGetKey(condition.getValue());
        String sql =key+" > " + value;


        return sql;
    }
}

package com.yulinlin.jdbc.mysql.parse.base;

import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class EqParse implements IParse<Eq> {

    @Override
    public String parse(Eq condition, IParamsContext params, IParseManager parseManager) {

        String key = AliasUtil.parse(condition,params) ;

        Object value = params.putGetKey(condition.getValue());

        String sql =key+" = " + value;

        return sql;
    }
}

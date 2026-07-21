package com.yulinlin.jdbc.mysql.parse.base;

import com.yulinlin.data.core.node.base.Lt;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class LtParse implements IParse<Lt> {

    @Override
    public String parse(Lt condition, IParamsContext params, IParseManager parseManager) {

        String key = AliasUtil.parse(condition,params) ;

        Object value = params.putGetKey(condition.getValue());
        String sql =key+" < " + value;


        return sql;
    }
}

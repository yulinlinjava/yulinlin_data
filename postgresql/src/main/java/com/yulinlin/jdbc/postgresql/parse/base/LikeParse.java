package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.Like;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

public class LikeParse implements IParse<Like> {

    @Override
    public String parse(Like condition, IParamsContext params, IParseManager parseManager) {


        String key = AliasUtil.parse(condition,params) ;

        Object value = params.putGetKey("%"+params.encode(condition.getValue())+"%");


        String sql =key+" like " + value+"";


        return sql;
    }
}

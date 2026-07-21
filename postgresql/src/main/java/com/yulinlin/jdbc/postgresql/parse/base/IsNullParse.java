package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.IsNull;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

public class IsNullParse implements IParse<IsNull> {

    @Override
    public String parse(IsNull condition, IParamsContext params, IParseManager parseManager) {

        String key = AliasUtil.parse(condition,params) ;

        String sql = key+" is null " ;

        return sql;
    }
}

package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.LikeRight;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

public class LikeRightParse implements IParse<LikeRight> {

    @Override
    public String parse(LikeRight condition, IParamsContext params, IParseManager parseManager) {

        String key = AliasUtil.parse(condition,params) ;
        Object encode = params.encode(condition.getValue());

        Object value = params.putGetKey(encode+"%");
        String sql =key+" like " + value;

        return sql;
    }
}

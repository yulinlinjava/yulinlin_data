package com.yulinlin.jdbc.mysql.parse.script;

import com.yulinlin.data.core.node.metrics.MaxMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class MaxFieldParse implements IParse<MaxMetrics> {

    @Override
    public Object parse(MaxMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;
        return "max("+key+")";
    }

}

package com.yulinlin.jdbc.postgresql.parse.script;

import com.yulinlin.data.core.node.metrics.CountMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;


public class CountFieldParse implements IParse<CountMetrics> {

    @Override
    public Object parse(CountMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;
        return "count("+key+")";
    }

}

package com.yulinlin.jdbc.mysql.parse.script;

import com.yulinlin.data.core.node.metrics.SumMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class SumFieldParse implements IParse<SumMetrics> {

    @Override
    public Object parse(SumMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;
        return "sum("+key+")";
    }

}

package com.yulinlin.jdbc.mysql.parse.script;

import com.yulinlin.data.core.node.metrics.AvgMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class AvgFieldParse implements IParse<AvgMetrics> {

    @Override
    public Object parse(AvgMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;
        return "avg("+key+")";
    }

}

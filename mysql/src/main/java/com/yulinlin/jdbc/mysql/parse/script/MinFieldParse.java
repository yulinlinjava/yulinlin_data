package com.yulinlin.jdbc.mysql.parse.script;

import com.yulinlin.data.core.node.metrics.MinMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class MinFieldParse implements IParse<MinMetrics> {

    @Override
    public Object parse(MinMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;
        return "min("+key+")";
    }

}

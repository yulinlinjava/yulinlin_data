package com.yulinlin.elasticsearch.parse.script;

import co.elastic.clients.elasticsearch._types.aggregations.SumAggregation;
import com.yulinlin.data.core.node.metrics.SumMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class SumFieldParse implements IParse<SumMetrics> {

    @Override
    public Object parse(SumMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);


        SumAggregation.Builder builder = new SumAggregation.Builder();

        return builder.field(key);
    }

}

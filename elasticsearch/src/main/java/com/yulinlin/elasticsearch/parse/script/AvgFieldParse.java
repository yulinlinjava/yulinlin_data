package com.yulinlin.elasticsearch.parse.script;

import co.elastic.clients.elasticsearch._types.aggregations.AverageAggregation;
import com.yulinlin.data.core.node.metrics.AvgMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class AvgFieldParse implements IParse<AvgMetrics> {

    @Override
    public Object parse(AvgMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        AverageAggregation.Builder builder = new AverageAggregation.Builder();


        return builder.field(key);


    }

}

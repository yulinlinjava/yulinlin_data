package com.yulinlin.elasticsearch.parse.script;


import co.elastic.clients.elasticsearch._types.aggregations.CardinalityAggregation;
import com.yulinlin.data.core.node.metrics.DistinctCountMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class DistinctCountParse implements IParse<DistinctCountMetrics> {

    @Override
    public Object parse(DistinctCountMetrics condition, IParamsContext params, IParseManager parseManager) {



        String key =AliasUtil.parse(condition,params);
        CardinalityAggregation.Builder builder = new CardinalityAggregation.Builder();

        return builder.field(key);
    }
}

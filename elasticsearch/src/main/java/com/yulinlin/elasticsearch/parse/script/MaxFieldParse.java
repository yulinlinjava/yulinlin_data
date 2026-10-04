package com.yulinlin.elasticsearch.parse.script;

import co.elastic.clients.elasticsearch._types.aggregations.MaxAggregation;
import com.yulinlin.data.core.node.metrics.MaxMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class MaxFieldParse implements IParse<MaxMetrics> {

    @Override
    public Object parse(MaxMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        MaxAggregation.Builder builder = new MaxAggregation.Builder();

        return builder.field(key).build();
    }

}

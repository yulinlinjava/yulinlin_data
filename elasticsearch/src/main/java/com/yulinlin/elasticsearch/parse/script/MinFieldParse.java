package com.yulinlin.elasticsearch.parse.script;

import co.elastic.clients.elasticsearch._types.aggregations.MinAggregation;
import com.yulinlin.data.core.node.metrics.MinMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class MinFieldParse implements IParse<MinMetrics> {

    @Override
    public Object parse(MinMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        MinAggregation.Builder builder = new MinAggregation.Builder();

        return builder.field(key);
    }

}

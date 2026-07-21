package com.yulinlin.elasticsearch.parse.script;

import co.elastic.clients.elasticsearch._types.aggregations.ValueCountAggregation;
import com.yulinlin.data.core.node.metrics.CountMetrics;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class CountFieldParse implements IParse<CountMetrics> {

    @Override
    public Object parse(CountMetrics condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);

        ValueCountAggregation.Builder builder = new ValueCountAggregation.Builder();

        return builder.field(key);
    }

}

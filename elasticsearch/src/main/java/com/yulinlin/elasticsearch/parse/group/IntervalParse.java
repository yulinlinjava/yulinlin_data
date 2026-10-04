package com.yulinlin.elasticsearch.parse.group;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class IntervalParse implements IParse<IntervalGroup> {




    @Override
    public Object parse(IntervalGroup condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);

            Aggregation.Builder.ContainerBuilder histogram = GroupUtil.get().histogram(f -> {
                f.field(key).interval((double) condition.getInterval());
                return f;
            });
            return histogram;

    }


}

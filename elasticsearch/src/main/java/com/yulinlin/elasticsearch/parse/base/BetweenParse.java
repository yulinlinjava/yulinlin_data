package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import com.yulinlin.data.core.node.base.Between;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class BetweenParse implements IParse<Between> {

    @Override
    public Query parse(Between condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);
        Object[] value =  condition.getValue().toArray();


        return QueryBuilders.range()
                .field(key)
                .gte(JsonData.of(value[0]))
                .lte(JsonData.of(value[1]))

                .build()._toQuery();
    }

}

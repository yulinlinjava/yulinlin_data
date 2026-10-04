package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import com.yulinlin.data.core.node.base.Lte;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class LteParse implements IParse<Lte> {

    @Override
    public Query parse(Lte condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);
        Object value =  condition.getValue();
        return   QueryBuilders.range()
                .field(key)
                .lte(JsonData.of(value))
                .build()._toQuery();


    }
}

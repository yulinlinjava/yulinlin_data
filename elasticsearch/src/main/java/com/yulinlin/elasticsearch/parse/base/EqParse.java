package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class EqParse implements IParse<Eq> {

    @Override
    public Query parse(Eq condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);
        Object value =  condition.getValue();

       return QueryBuilders.term()
                .field(key)
                .value(value.toString())
                .build()._toQuery();

    }
}

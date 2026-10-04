package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.yulinlin.data.core.node.base.Ne;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class NeParse implements IParse<Ne> {

    @Override
    public Query parse(Ne condition, IParamsContext params, IParseManager parseManager) {


        String key =AliasUtil.parse(condition,params);
        Object value =  condition.getValue();

        TermQuery build = QueryBuilders.term()
                .field(key)
                .value(value.toString())
                .build();

        return QueryBuilders.bool()
                .mustNot(build._toQuery())
                .build()._toQuery();
    }
}

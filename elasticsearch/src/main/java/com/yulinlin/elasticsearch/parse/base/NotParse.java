package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

public class NotParse implements IParse<Not> {

    @Override
    public Query parse(Not condition, IParamsContext params, IParseManager parseManager) {
        And and = new And(condition.getList());
        Query queryBuilder =  (Query)  parseManager.parse(and,params);
        if(queryBuilder == null){
            return null;
        }
        return QueryBuilders.bool()
                .mustNot(queryBuilder)
                .build()._toQuery();
    }
}

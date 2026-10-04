/*
package com.yulinlin.mongodb.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.node.Nested;
import com.yulinlin.elasticsearch.parse.AliasUtil;
import com.yulinlin.mongodb.parse.AliasUtil;

public class NestedParse implements IParse<Nested> {

    @Override
    public Query parse(Nested condition, IParamsContext params, IParseManager parseManager) {




        String key =AliasUtil.parse(condition,params);

        Query query = (Query)parseManager.parse(condition.getValue(), params);

        return QueryBuilders.nested()
                .path(key)
                .query(query).build()._toQuery();

    }
}
*/

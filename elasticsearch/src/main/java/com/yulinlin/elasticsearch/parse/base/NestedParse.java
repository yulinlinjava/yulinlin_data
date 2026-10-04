package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.node.NestedNode;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class NestedParse implements IParse<NestedNode> {

    @Override
    public Query parse(NestedNode condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);

        Query query = (Query)parseManager.parse(condition.getValue(), params);

        return QueryBuilders.nested()
                .path(key)
                .query(query).build()._toQuery();

    }
}

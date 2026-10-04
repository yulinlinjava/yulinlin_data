package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.node.base.Like;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class LikeParse implements IParse<Like> {

    @Override
    public Query parse(Like condition, IParamsContext params, IParseManager parseManager) {



        String key =AliasUtil.parse(condition,params);
        Object value =  condition.getValue();

        MatchQuery queryBuilder =  QueryBuilders.match()
                .field(key)
                .query(value.toString())

                .build()
                ;

        return queryBuilder._toQuery();
    }
}

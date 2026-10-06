package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.node.base.Match;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

/** Elasticsearch's native analyzed match query. */
public class MatchParse implements IParse<Match> {
    @Override
    public Query parse(Match condition, IParamsContext params, IParseManager parseManager) {
        return QueryBuilders.match()
                .field(AliasUtil.parse(condition, params))
                .query(condition.getValue())
                .build()
                ._toQuery();
    }
}

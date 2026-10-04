package com.yulinlin.elasticsearch.parse.predicate;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

public class AndParse implements IParse<And> {

    @Override
    public Query parse(And condition, IParamsContext params, IParseManager parseManager) {


        return QueryUtil.parse(condition,params,parseManager,true);
    }
}

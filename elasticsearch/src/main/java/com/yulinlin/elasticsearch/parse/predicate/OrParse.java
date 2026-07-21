package com.yulinlin.elasticsearch.parse.predicate;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yulinlin.data.core.node.predicate.Or;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

public class OrParse implements IParse<Or> {



    @Override
    public Query parse(Or condition, IParamsContext params, IParseManager parseManager) {

        return QueryUtil.parse(condition,params,parseManager,false);


    }
}

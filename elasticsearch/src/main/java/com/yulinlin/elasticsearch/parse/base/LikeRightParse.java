package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.node.base.LikeRight;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class LikeRightParse implements IParse<LikeRight> {

    @Override
    public Query parse(LikeRight condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);
        Object value =  condition.getValue();
/*        return QueryBuilders.wildcard()
                .field(key)
                .value(value+"*")
                .build()._toQuery();*/

       return QueryBuilders.prefix()
                .field(key)
                .value(value.toString())
                .build()._toQuery()
                ;


    }
}

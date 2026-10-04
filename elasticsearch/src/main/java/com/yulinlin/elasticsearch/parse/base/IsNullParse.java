package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.node.base.IsNull;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class IsNullParse implements IParse<IsNull> {

    @Override
    public Object parse(IsNull condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);

       return QueryBuilders.exists(f ->f.field(key));

    }
}

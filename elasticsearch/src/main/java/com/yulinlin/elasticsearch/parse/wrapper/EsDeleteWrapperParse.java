package com.yulinlin.elasticsearch.parse.wrapper;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.DeleteWrapper;

public class EsDeleteWrapperParse implements IParse<DeleteWrapper> {

    @Override
    public Object parse(DeleteWrapper condition, IParamsContext params, IParseManager parseManager) {
        String index = (String) parseManager.parse(condition.getFrom(),params);


        DeleteByQueryRequest.Builder request = new DeleteByQueryRequest.Builder();


          request.index(index);

        Query queryBuilder = (Query) parseManager.parse(condition.where(),params);
        if(queryBuilder != null){
            request.query(queryBuilder);

        }

        return new ParseResult(ParseType.delete,request.build(),params);


    }
}

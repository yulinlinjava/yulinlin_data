package com.yulinlin.elasticsearch.parse.wrapper;


import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;

public class EsUpdateWrapperParse implements IParse<UpdateWrapper> {




    private UpdateByQueryRequest updateByQueryRequest(UpdateWrapper condition, IParamsContext params, IParseManager parseManager) {

        Script script  =(Script) parseManager.parse(condition.fields(), params);

        String index = (String) parseManager.parse(condition.getFrom(),params);

        UpdateByQueryRequest.Builder request = new UpdateByQueryRequest.Builder();
        request.script(script);
        request.index(index);


        Query queryBuilder = (Query) parseManager.parse(condition.where(),params);

        if(queryBuilder != null){
            request.query(queryBuilder);

        }



        return request.build();

    }

        @Override
    public Object parse(UpdateWrapper condition, IParamsContext params, IParseManager parseManager) {


            UpdateByQueryRequest updateByQueryRequest =  updateByQueryRequest(condition,params,parseManager);


            return new ParseResult(ParseType.delete,updateByQueryRequest,params);


    }
}

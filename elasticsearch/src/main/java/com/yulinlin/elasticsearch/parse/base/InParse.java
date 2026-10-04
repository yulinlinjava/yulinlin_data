package com.yulinlin.elasticsearch.parse.base;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.yulinlin.data.core.node.base.In;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class InParse implements IParse<In> {

    @Override
    public Query parse(In condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);
        Collection<Object> collection =  condition.getValue();

        List<FieldValue> collect = collection.stream().map(row -> {
            if( row instanceof Long){
               return FieldValue.of((long)row);
            }else {
               return   FieldValue.of(row.toString());
            }

        }).collect(Collectors.toList());



        return QueryBuilders.terms()
                .field(key)
                .terms(f -> {
                    f.value(collect);
                    return f;
                }).build()._toQuery()
                ;
    }
}

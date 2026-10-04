package com.yulinlin.elasticsearch.parse.predicate;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yulinlin.data.core.node.ICondition;
import com.yulinlin.data.core.node.predicate.Predicates;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.enums.EsKeys;

import java.util.ArrayList;

class QueryUtil {

     public static Query parse(Predicates condition, IParamsContext params, IParseManager parseManager,boolean isAnd){

         BoolQuery.Builder builder = new BoolQuery.Builder();
         ArrayList<Query> objects = new ArrayList<>();
         for (ICondition iCondition : condition.getList()) {
             Query query = (Query)parseManager.parse(iCondition, params);

             if(query != null){
                 objects.add(query);

             }
         }
         if(objects.size() == 0){
             return null;
         }else if(objects.size() == 1){
             return objects.get(0);
         }
         Float boost =  condition.get(EsKeys.boots,1.0F);
         if(isAnd){
             return builder.must(objects).boost(boost).build()._toQuery();
         }else {
            String minimumShouldMatch =  condition.get(EsKeys.minimumShouldMatch,"1");

             builder.minimumShouldMatch(minimumShouldMatch);

             builder.boost(boost);
             return builder.should(objects).build()._toQuery();
         }

     }
}

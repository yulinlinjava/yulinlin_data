package com.yulinlin.elasticsearch.parse.wrapper;

import co.elastic.clients.elasticsearch._types.InlineScript;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationVariant;
import co.elastic.clients.elasticsearch._types.aggregations.BucketSelectorAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.util.ObjectBuilder;
import com.yulinlin.data.core.node.select.FunctionAsField;
import com.yulinlin.data.core.node.select.GroupAsField;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.impl.AggregationsWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.MetricsWrapper;
import com.yulinlin.elasticsearch.parse.group.GroupUtil;
import com.yulinlin.elasticsearch.util.JsScriptUtil;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class EsGroupWrapperParse implements IParse<GroupWrapper> {


    private Query buildQuery( GroupWrapper condition, IParamsContext params, IParseManager parseManager){


        //查询条件
        Query where = (Query)parseManager.parse(condition.where(),params);


        return where;

    }



    private BucketSelectorAggregation buildHaving(       IConditionWrapper having){
        BucketSelectorAggregation.Builder builder = new BucketSelectorAggregation.Builder();

        JsScriptUtil.Node node = JsScriptUtil.parse(having);
        if(node == null){
            return null;
        }

        Map<String, String> dict = node.getKeys().stream().collect(Collectors.toMap(row -> row, row -> row));

        builder.bucketsPath(s -> s.dict(dict));
        InlineScript.Builder inlineScript = new InlineScript.Builder();
        inlineScript.lang("painless");
        inlineScript.source(node.getJs());





        Script script = new Script.Builder().inline(inlineScript.build()).build();

        builder.script(script);




        return builder.build();
    }


    private    void buildAggregation(
            Aggregation.Builder root,
            AggregationsWrapper groups,

            MetricsWrapper functions,

                                 IConditionWrapper having,
                                 int i,


                                                        IParamsContext params, IParseManager parseManager){

        if(i< groups.getList().size()){

            GroupAsField group = (GroupAsField)groups.getList().get(i);

            String alias = group.getAlias();

            root.aggregations(alias,f -> {
                GroupUtil.set(f);

                Aggregation.Builder.ContainerBuilder terms =(Aggregation.Builder.ContainerBuilder) parseManager.parse(group.getGroup(),params);
                GroupUtil.set(null);
                buildAggregation(f,groups,functions,having,i+1,params,parseManager);
                return terms;
            });



        }else {


            GroupUtil.set(root);
            Collection<FunctionAsField> list = functions.getList();

            for (FunctionAsField function :list) {
                String alias = function.getAlias();

                ObjectBuilder<AggregationVariant> builder =(ObjectBuilder) parseManager.parse(function.getFunction(),params);
                AggregationVariant build = builder.build();

                root.aggregations(alias,build._toAggregation());
            }
            GroupUtil.set(null);

            BucketSelectorAggregation bucketSelectorAggregation = buildHaving(having);


            if(bucketSelectorAggregation != null){
                root.aggregations("having_filter",hf -> hf.bucketSelector(bucketSelectorAggregation));

            }

        }


    }



    @Override
    public ParseResult parse(GroupWrapper condition, IParamsContext params, IParseManager parseManager) {




        Query query =buildQuery(condition,params,parseManager);


        String index = (String) parseManager.parse(condition.getFrom(),params);

        Aggregation.Builder aggBuilder = new Aggregation.Builder();




         buildAggregation(aggBuilder, (AggregationsWrapper)condition.aggregations(), (MetricsWrapper)condition.metrics(),
                 condition.having(),
                 0, params, parseManager);

       // Map<String, Aggregation> aggregations =  (    Map<String, Aggregation>)  ReflectionUtil.invokeGetter(aggBuilder,"aggregations");
        Map<String, Aggregation> aggregations =  aggBuilder.sum(f->f.field("a")).build().aggregations();




        SearchRequest.Builder builder = new SearchRequest.Builder();
        SearchRequest build = builder.index(index)
                .size(0)
                .aggregations(aggregations)

                .query(query)
                .build();
        return new ParseResult(ParseType.group,build,params);


    }
}

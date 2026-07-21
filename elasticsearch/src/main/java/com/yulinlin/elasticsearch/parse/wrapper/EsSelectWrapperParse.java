package com.yulinlin.elasticsearch.parse.wrapper;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.yulinlin.data.core.node.order.OrderNode;
import com.yulinlin.data.core.node.select.AsField;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.AsFieldListWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.elasticsearch.enums.EsKeys;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class EsSelectWrapperParse implements IParse<SelectWrapper> {


    private Query buildQuery(SelectWrapper condition, IParamsContext params, IParseManager parseManager){


        //查询条件
        Query where = (Query)parseManager.parse(condition.where(),params);


        return where;

    }


    @Override
    public ParseResult parse(SelectWrapper condition, IParamsContext params, IParseManager parseManager) {



        Query query =buildQuery(condition,params,parseManager);


        String index = (String) parseManager.parse(condition.getFrom(),params);


        SearchRequest.Builder builder = new SearchRequest.Builder();

        for (OrderNode orderNode : condition.getOrder().getList()) {
            SortOptions.Builder sort = new SortOptions.Builder();

            sort.field( f ->{
                        SortOrder sortOrder =   SortOrder.Asc;
                        if(!orderNode.isAsc()){
                            sortOrder = SortOrder.Desc;
                        }
                     String name = AliasUtil.parse( orderNode,params);

                    return f.field(name).order(sortOrder);
            });

            builder.sort(sort.build());
        }

        if(condition.getPageNumber() > 0){
            int from = (condition.getPageNumber() - 1) * condition.getPageSize();
            builder.from(from)
                    .size(condition.getPageSize());

        }

        AsFieldListWrapper<AsField> fields =(AsFieldListWrapper) condition.fields();
        for (AsField field : fields.getList()) {
            String key = AliasUtil.parse(field, params);

            builder.fields(f -> {
                return f.field(key);
            });

            String preTags = (String)field.getMeta().get(EsKeys.preTags);
            String postTags = (String)field.getMeta().get(EsKeys.postTags);
            if(preTags == null || postTags == null){
                continue;
            }
            builder.highlight(f -> {
                return f.fields(key,h -> {
                    return h.preTags(preTags)
                            .postTags(postTags);
                });

            });
        }

        builder.source(f -> f.fetch(false));
        SearchRequest build = builder.index(index)

                .size(condition.getPageSize())
                .query(query)
                .build();

        return new ParseResult(ParseType.delete,build,params);


    }
}

package com.yulinlin.jdbc.postgresql.parse;


import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.jdbc.session.SqlNode;

import java.util.Map;

public class ExpressionNodeParse implements IParse<CommandNode<String>> {

    @Override
    public Object parse(CommandNode<String> condition, IParamsContext params, IParseManager parseManager) {
        if(condition.getParams() != null){
            for (Map.Entry<String, Object> stringObjectEntry : condition.getParams().entrySet()) {
                params.put("#{"+stringObjectEntry.getKey()+"}",stringObjectEntry.getValue());
            }
        }


        SqlNode node =  new SqlNode(condition.getExpression(),  params.getDataBuffer());

        return new ParseResult(condition.getType(),node,params);



    }
}

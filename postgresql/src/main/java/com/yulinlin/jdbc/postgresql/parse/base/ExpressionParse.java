package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.Expression;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;


public class ExpressionParse implements IParse<Expression>  {

    @Override
    public String parse(Expression condition, IParamsContext params, IParseManager parseManager) {
        String expression =  condition.getExpression();
        Object parse = params.parse(expression);
        params.put(condition.getParams());
        return parse.toString();
    }
}

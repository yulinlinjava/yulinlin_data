package com.yulinlin.data.core.node.base;


import com.yulinlin.data.core.node.ICondition;
import com.yulinlin.data.core.node.INode;

import java.util.Map;

public class Expression implements ICondition {

    private String  expression;

    private Map<String,Object> params;

    public Expression(String expression, Map<String, Object> params) {
        this.expression = expression;
        this.params = params;
    }

    public String getExpression() {
        return expression;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}

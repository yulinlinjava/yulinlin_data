package com.yulinlin.data.core.node;

import com.yulinlin.data.core.parse.ParseType;

import java.util.Map;

public class CommandNode<E> implements INode {

    private E expression;

    private Map<String,Object> params;

    private ParseType type;

    public CommandNode(E expression, ParseType type) {
        this.expression = expression;
        this.type = type;
    }

    public CommandNode(E expression, Map<String, Object> params, ParseType type) {
        this.expression = expression;
        this.params = params;
        this.type = type;
    }

    public E getExpression() {
        return expression;
    }

    public ParseType getType() {
        return type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public static <E> CommandNode of(E command, ParseType type){
        return new CommandNode(command,type);
    }
    public static <E> CommandNode of(E command, Map<String,Object> params, ParseType type){
        return new CommandNode(command,params,type);
    }

}

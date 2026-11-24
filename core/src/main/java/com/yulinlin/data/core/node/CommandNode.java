package com.yulinlin.data.core.node;

import com.yulinlin.data.core.parse.ParseType;

import java.util.Map;

public class CommandNode<E> implements INode {

    private E expression;

    private Map<String,Object> params;

    private ParseType type;

    /** 原生查询最多读取的行数，null 表示不限。JDBC 用 Statement.setMaxRows 在驱动层截断；其他数据源忽略。 */
    private Integer maxRows;

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

    public Integer getMaxRows() {
        return maxRows;
    }

    /** 见 {@link #getMaxRows()}；必须大于 0。返回自身便于链式调用。 */
    public CommandNode<E> maxRows(Integer maxRows) {
        if (maxRows != null && maxRows < 1) throw new IllegalArgumentException("maxRows 必须大于 0");
        this.maxRows = maxRows;
        return this;
    }

    public static <E> CommandNode of(E command, ParseType type){
        return new CommandNode(command,type);
    }
    public static <E> CommandNode of(E command, Map<String,Object> params, ParseType type){
        return new CommandNode(command,params,type);
    }

}

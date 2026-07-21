package com.yulinlin.data.core.anno;


/**
 * 运算符
 */
public enum ConditionEnum {
    

    // 等于
    eq("=","相等"),
    // 不等
    ne("!=","不等"),
    // 模糊
    like("like","模糊"),

    //右模糊
    likeRight("like","右模糊"),
    //多次模糊查询

    // 属于
    in("in","属于"),
    // 不属于


    // 大于
    gt(">","大于"),
    // 大于等于
    gte(">=","大于等于"),

    // 小于
    lt("<","小于"),
    // 小于等于
    lte("<=","小于等于"),
    //范围
    between("between","范围"),
    isNull("is null","空"),
    ;

    private final String symbol;
    private final String name;


    ConditionEnum(String value, String name) {
        this.symbol = value;
        this.name = name;
    }



    public String getSymbol() {
        return this.symbol;
    }

    public String getLabel() {
        return this.name;
    }
    public String getValue() {
        return this.name();
    }
}
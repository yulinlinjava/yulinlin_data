package com.yulinlin.data.core.anno;



/**
 * 运算符
 */
public enum JoinEnum {


    //顺序
    left("左链接"),
    right("右链接"),
    //逆序
    inner("内链接"),
    ;

    private final String value;

    JoinEnum(String value) {
        this.value = value;
    }


    public String getLabel() {
        return this.value;
    }

    public String getValue() {
        return this.name();
    }
}

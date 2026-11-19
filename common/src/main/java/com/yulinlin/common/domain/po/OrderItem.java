package com.yulinlin.common.domain.po;

import io.swagger.v3.oas.annotations.media.Schema;


public class OrderItem {

    @Schema(description = "实体 Java 属性名，必须与字段名一致，支持继承属性；不接受数据库列名或 SQL 表达式")
    private String name;

    private boolean asc;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAsc() {
        return asc;
    }

    public void setAsc(boolean asc) {
        this.asc = asc;
    }
}

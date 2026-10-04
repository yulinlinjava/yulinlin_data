package com.yulinlin.data.core.node.from;

public class Store implements From {

    private String name;

    private String  alias;

    public Store(String name) {
        this.name = name;
    }

    public Store(String name, String alias) {
        this.name = name;
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

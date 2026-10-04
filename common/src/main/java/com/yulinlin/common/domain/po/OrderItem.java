package com.yulinlin.common.domain.po;

import lombok.Data;


public class OrderItem {

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

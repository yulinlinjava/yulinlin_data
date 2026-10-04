package com.yulinlin.admin;

import com.yulinlin.data.lang.enums.IEnum;

public enum RoleType implements IEnum<String> {

    ok("1"),

    error("2"),
    ;




    private String label;

    RoleType(String label) {
        this.label = label;
    }


    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getValue() {
        return label;
    }
}

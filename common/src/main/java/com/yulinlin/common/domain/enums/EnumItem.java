package com.yulinlin.common.domain.enums;

public class EnumItem {

    private String label;

    private Object value;

    private Object data;

    public EnumItem(String label, Object value) {
        this.label = label;
        this.value = value;
    }

    public EnumItem(String label, Object value, Object data) {
        this.label = label;
        this.value = value;
        this.data = data;
    }

    public Object getData() {
        return data;
    }

    public String getLabel() {
        return label;
    }

    public Object getValue() {
        return value;
    }
}

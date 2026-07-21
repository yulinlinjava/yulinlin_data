package com.yulinlin.data.core.anno;

public enum MetricsEnum {

    count("总数"),
    distinctCount("去重复总数"),
    sum("求和"),
    avg("平均"),
    min("最小"),
    max("最大"),

    ;

    private String label;

    private boolean where;

    MetricsEnum(String label) {
        this.label = label;
    }

    MetricsEnum(String label, boolean where) {
        this.label = label;
        this.where = where;
    }

    public boolean isWhere() {
        return where;
    }

    public String getLabel() {
        return label;
    }
}

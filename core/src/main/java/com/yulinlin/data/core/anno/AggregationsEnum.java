package com.yulinlin.data.core.anno;

public enum AggregationsEnum {

    field("列"),
    minute("分钟"),
    hour("小时"),
    day("天"),
    month("月"),
    quarter("季度"),
    year("年"),
    interval("数字区间")
    ;

    private String label;



    AggregationsEnum(String label) {
        this.label = label;
    }





    public String getLabel() {
        return label;
    }
}

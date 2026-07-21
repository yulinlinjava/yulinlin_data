package com.yulinlin.data.core.node.group;

import com.yulinlin.data.core.node.MetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

public class DateGroup extends MetaNode implements IRangeGroup {



    private Type dateType;

    public DateGroup(Object name, Type dateType) {
        super(name);
        this.dateType = dateType;
    }

    public Type getDateType() {
        return dateType;
    }

    public enum Type{
        //分钟
        minute("yyyy-MM-dd HH:mm:00"),
        //小时
        hour("yyyy-MM-dd HH:00:00"),
        //天
        day("yyyy-MM-dd"),
        //月
        month("yyyy-MM"),
        //季度
        quarter("yyyy-MM"),

        //年
        year("yyyy-MM"),

        ;

        private String format;

        Type(String format) {
            this.format = format;
        }


        public String getFormat() {
            return format;
        }
    }

}

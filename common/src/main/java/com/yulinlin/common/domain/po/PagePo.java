package com.yulinlin.common.domain.po;


import com.yulinlin.data.core.anno.ConditionEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "分页模型")
@Data
public class PagePo {



    @Schema(description = "分页下标")
    private int pageNumber = 1;

    @Schema(description = "分页高度")
    private int pageSize = 15;

    @Schema(description = "排序列表 ")
    private List<OrderItem> orderList;



    private List<Field> and = new ArrayList<>();

    private List<Field> or = new ArrayList<>();


    public PagePo and(String name, ConditionEnum condition, Object value){
        and.add(new Field(name,condition,value));
        return this;
    }


    public PagePo or(String name,ConditionEnum condition,Object value){
        or.add(new Field(name,condition,value));
        return this;
    }



    @Data
    public static class Field{
        @Schema(description = "实体 Java 属性名，必须与字段名一致，支持继承属性；不接受数据库列名或 SQL 表达式")
        private String name;
        private ConditionEnum condition;
        private Object value;

        public Field() {
        }

        public Field(String name, ConditionEnum condition, Object value) {

            this.name = name;
            this.condition = condition;
            this.value = value;
        }


    }
}

package com.yulinlin.common.domain.po;


import com.yulinlin.data.core.anno.ConditionEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
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

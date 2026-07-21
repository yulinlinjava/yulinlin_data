package com.yulinlin.common.domain;

import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.util.MaxNumberUtil;
import com.yulinlin.common.util.SnowflakeUtil;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;


public class IdEntity<E extends IdEntity<E>>   implements Serializable , AbstractModel<E>{

    @JoinWhere
    @JoinMeta(primaryKey = true)
    @JoinField
    @ApiModelProperty("id")
    private String id;



    @Override
    public void insertBefore() {
        if(this.id == null || id.isEmpty()){
            this.id =generateId();
        }
    }

    public String nextIncrId(){
        AtomicLong longAdder = MaxNumberUtil.of(this.getClass());
        String v = longAdder.incrementAndGet()+"";
        return v;
    }

    public String generateId(){
        return  SnowflakeUtil.nextIdStr();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}

package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;


public class IntegerCoder implements ICoder<Integer,Integer> {

    @Override
    public Integer encode(IDataBuffer buffer, String key, Integer value) {
        return value;
    }



    @Override
    public Integer decode(IDataBuffer buffer , Field field, Object value) {
        if(value.getClass().isPrimitive()){
            return (Integer) value;
        }

        return new BigDecimal(value.toString()).intValue();
    }

    @Override
    public boolean check(Class clazz) {
        return ICoder.super.check(clazz) || clazz == int.class;
    }
}

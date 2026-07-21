package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;


public class FloatCoder implements ICoder<Float,Float> {

    @Override
    public Float encode(IDataBuffer buffer, String key, Float value) {
        return value;
    }

    @Override
    public Float decode(IDataBuffer buffer , Field field, Object value) {
        if(value.getClass().isPrimitive()){
            return (Float) value;
        }
        return new BigDecimal(value.toString()).floatValue();
    }



    @Override
    public boolean check(Class clazz) {
        return ICoder.super.check(clazz) || clazz == float.class;
    }
}

package com.yulinlin.data.core.coder.impl;


import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;


public class LongCoder implements ICoder<Long, Long > {

    @Override
    public Long encode(IDataBuffer buffer, String key, Long value) {
        return value;
    }

    @Override
    public Long decode(IDataBuffer buffer , Field field, Object value) {
        if(value.getClass().isPrimitive()){
            return (Long)value;
        }
        return new BigDecimal(value.toString()).longValue();
    }



    @Override
    public boolean check(Class clazz) {
        return ICoder.super.check(clazz) || clazz == long.class;
    }
}

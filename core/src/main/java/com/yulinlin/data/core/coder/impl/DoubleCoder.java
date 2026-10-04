package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;


public class DoubleCoder implements ICoder<Double,Double> {


    @Override
    public Double encode(IDataBuffer buffer, String key, Double value) {
        return value;
    }



    @Override
    public Double decode(IDataBuffer buffer , Field field, Object value) {
        if(value.getClass().isPrimitive()){
            return (Double)value;
        }
        return new BigDecimal(value.toString()).doubleValue();
    }

    @Override
    public boolean check(Class clazz) {
        return ICoder.super.check(clazz) || clazz == double.class;
    }

}

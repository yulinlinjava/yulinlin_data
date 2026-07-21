package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;


public class BooleanCoder implements ICoder<Boolean,Boolean> {

    @Override
    public Boolean encode(IDataBuffer buffer, String key, Boolean value) {
        return value;
    }

    @Override
    public Boolean decode(IDataBuffer buffer , Field field, Object value) {
        Object number =value;
        if(number instanceof Boolean){
            return (Boolean)number;
        }else if(number instanceof String){
            return !number.equals("0");
        }else if (number instanceof Number){
            Number n = (Number) number;
            return n.intValue() > 0;
        }
        return Boolean.parseBoolean(number.toString());
    }




    @Override
    public boolean check(Class clazz) {
        return ICoder.super.check(clazz) || clazz == boolean.class;
    }
}

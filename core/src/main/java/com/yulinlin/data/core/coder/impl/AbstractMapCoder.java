package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.json.JsonUtil;

import java.lang.reflect.Field;
import java.util.Map;


public abstract class AbstractMapCoder<E> implements ICoder<Map,E> {

    @Override
    public Map decode(IDataBuffer buffer, Class clazz, Object value) {
        if(value instanceof String){
            return (Map)JsonUtil.parseJson(value.toString(),clazz);
        }
        return buffer.toMap();
    }



    @Override
    public Map decode(IDataBuffer buffer , Field field, Object value) {
        if(value == null){
            return null;
        }
        if(value instanceof String){
            return JsonUtil.parseByField(value.toString(),field);
        }
        return buffer.toMap();
    }



    @Override
    public boolean check(Class clazz) {
        return Map.class.isAssignableFrom(clazz);
    }

    @Override
    public int priority() {
        return 2;
    }

}

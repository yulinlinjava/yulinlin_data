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

/*
    protected Map encodeObject(IDataBuffer buffer, String key, Map<String,Object> value) {
        HashMap<Object, Object> data = new HashMap<>();


        for (Map.Entry<String, Object> entry : value.entrySet()) {

            Object encode = buffer.encode(entry.getValue());
            data.put(entry.getKey(),encode);

        }
        return data;
    }


    @Override
    public Map decode(IDataBuffer buffer, Class aClass, Object value) {

        if(aClass.isAssignableFrom(value.getClass())){
            return (Map)value;
        }
        if(value instanceof String){
            Map map = JsonUtil.parseJson(value.toString(), Map.class);

            return map;
        }
        throw new NoticeException("不支持解码");

    }

    @Override
    public Map decode(IDataBuffer buffer, Field key, Object value) {
        Class aClass = GenericUtil.getFieldGeneric(key,1);


        Map<String,Object> map = decode(buffer,key.getType(),value);
   
        if(aClass == null || ClassUtil.isPrimitive(aClass) || Object.class == aClass){
            return map;
        }

        HashMap<String, Object> data = new HashMap<>();


        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val =  buffer.decode(entry.getValue(),aClass);
            data.put(entry.getKey(),val);
        }

        return data;
    }*/

    @Override
    public boolean check(Class clazz) {
        return Map.class.isAssignableFrom(clazz);
    }

    @Override
    public int priority() {
        return 2;
    }

}

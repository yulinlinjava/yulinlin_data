package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.json.JsonUtil;
import com.yulinlin.data.lang.util.ClassUtil;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;


public abstract class AbstractCollectionCoder<E > implements ICoder<Collection,E> {

    @Override
    public Collection decode(IDataBuffer buffer, Class clazz, Object value) {
        if(value instanceof String){
            return (Collection) JsonUtil.parseJson(value.toString(),clazz);
        }
        Collection coll =(Collection) value;
        Object o = coll.toArray(new Object[1])[0];
        if(ClassUtil.isPrimitive(o.getClass())){
            return (Collection) value;
        }
        Collection<? extends IDataBuffer> list =(Collection) value;
        return list.stream().map(row -> row.toMap()).collect(Collectors.toList());

    }

    @Override
    public Collection decode(IDataBuffer buffer, Field field, Object value) {
        if(value == null){
            return null;
        }
        if(value instanceof String){
            return (Collection) JsonUtil.parseByField(value.toString(),field);
        }


        Class<?> type = GenericUtil.forField(field, 0);

        if(ClassUtil.isPrimitive(type)){
            return (Collection) value;
        }

        Collection<IDataBuffer> list =(Collection) value;
        if(type == null ){
            return list.stream().map(row -> row.toMap()).collect(Collectors.toList());
        }
        return list.stream().map(row -> row.decode(type)).collect(Collectors.toList());

    }





    @Override
    public boolean check(Class clazz) {
        return getTypeClass().isAssignableFrom(clazz);
    }

    @Override
    public int priority() {
        return 1;
    }
}

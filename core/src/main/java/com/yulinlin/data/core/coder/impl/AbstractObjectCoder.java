
package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.json.JsonUtil;

import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractObjectCoder<E> implements ICoder<Object,E> {

    public static boolean isNumber(String str) {
        if(str.length() == 0 || str.length() >= 11){
            return false;
        }
        char[] chars = str.toCharArray();
        for (char chr : chars) {
            if (chr < 48 || chr > 57)
                return false;
        }
        return true;
    }



    private Object  objectDecode(IDataBuffer buffer, Class clazz, Object value){
        //特殊情况处理
        if(clazz == Object.class && value instanceof String){
            String text = value.toString();
            if(text.length() == 0){
                return null;
            }
            try {
                if(text.startsWith("<")){
                    return value;
                } else if(text.startsWith("[")){
                    return JsonUtil.parseJson(text,List.class);
                }else if(text.startsWith("{")){
                    return JsonUtil.parseJson(text,Map.class);
                }else if(isNumber(text)){
                    if(text.contains(".")){
                        return Double.parseDouble(text);
                    }else {
                        return Long.parseLong(text);
                    }
                }else if(DateTime.isDate(text)){
                    return DateTime.parse(text);
                } else {
                    return value;
                }
            }catch (Exception e){
                log.info("反序列化失败 {} {}",clazz,text);
                return null;
            }

        }
        return null;
    }

    @Override
    public Object decode(IDataBuffer buffer, Class clazz, Object value) {
        Object o = objectDecode(buffer, clazz, value);
        if(o != null){
            return o;
        }
        Object obj = ReflectionUtil.newInstance(clazz);
        for (Field f : ReflectionUtil.getAllDeclaredFields(clazz)) {
            String name =f.getName();
            Object object = buffer.getObject(name,f);
            if(object == null){
                continue;
            }
            ReflectionUtil.invokeSetter(obj, name, object);
        }

        return obj;


    }

    @Override
    public Object decode(IDataBuffer buffer, Field field, Object value) {

        if(value == null){
            return value;
        }

        Class clazz = field.getType();
        Object o = objectDecode(buffer, clazz, value);
        if(o != null){
            return o;
        }
        if(value instanceof String){
            return JsonUtil.parseByField(value.toString(),field);
        }

        return decode(buffer,clazz,value);


    }


    @Override
    public boolean check(Class clazz) {
        return true;
    }

     @Override
    public int priority() {
        return 1;
    }
}


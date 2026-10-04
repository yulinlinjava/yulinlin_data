package com.yulinlin.data.core.coder;

import com.yulinlin.data.core.exception.NoticeException;

import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractDataBuffer implements IDataBuffer {


    private boolean encoder;

    private ICoderManager coderManager;


    public AbstractDataBuffer(boolean encoder,ICoderManager coderManager) {
        this.encoder = encoder;
        this.coderManager = coderManager;
    }


    protected  abstract  void executeEncoderPut(String key,Object value);

    @Override
    public Object encode(Object value) {
        Class<?> clazz = value.getClass();
        if(clazz == String.class || clazz.isPrimitive()){
            return value;
        }
        return   coderManager.getCoder(value.getClass()).encode(this,null,value);
    }

    @Override
    public Object decode(Class clazz) {

        if (clazz == null || Object.class == clazz || Map.class.isAssignableFrom(clazz)) {
            return this.toMap();
        }

            return coderManager.getCoder(clazz).decode(this,clazz,this);



    }
    @Override
    public Object decode(Object value, Class clazz) {
        ICoder coder = coderManager.getCoder(clazz);
        Object decode = coder.decode(this, clazz, value);

        return decode;
    }
    @Override
    public Object decode(Object value, Field clazz) {
        Object decode = coderManager.getCoder(clazz.getType()).decode(this, clazz, value);

        return decode;
    }
    @Override
    public Object getObject(String key, Field clazz) {
        Object object = getObject(key);
        if(object == null){
            return null;
        }
        if(!encoder){
            Class<?> type = clazz.getType();

            if(type == String.class){
                return object;
            }
            else if(type == Integer.class){
                return   new BigDecimal(object.toString()).intValue();
            }
            else if(type == Long.class){
                return   new BigDecimal(object.toString()).longValue();
            }
            else if(type == Float.class){
                return   new BigDecimal(object.toString()).floatValue();
            }
            else if(type == Double.class){
                return   new BigDecimal(object.toString()).doubleValue();
            }  else if(type == DateTime.class){
                return   DateTime.parse(object.toString());
            } else if(type == BigDecimal.class){
                return   new BigDecimal(object.toString());
            } else if(type == BigInteger.class){
                return   new BigInteger(object.toString());
            }

            return   getCoderManager().getCoder(type).decode(this,clazz,object);
        }
        return  object;
    }



    @Override
    public IDataBuffer put(String key, Object val) {
        if(encoder){
             val =  encode(val);
        }
        executeEncoderPut(key,val);
        return this;
    }


    @Override
    public <E> E getObject(String key, Class clazz) {
        Object object = getObject(key);
        if(object == null){
            return null;
        }
        if(!encoder){
            Object decode = decode(object,clazz);
            return (E)decode;
        }
        return (E)object;
    }

    public boolean isEncoder() {
        return encoder;
    }

    public ICoderManager getCoderManager() {
        return coderManager;
    }
}

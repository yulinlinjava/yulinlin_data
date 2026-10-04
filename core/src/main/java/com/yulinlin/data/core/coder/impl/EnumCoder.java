package com.yulinlin.data.core.coder.impl;


import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.enums.IEnum;

import java.lang.reflect.Field;


public class EnumCoder implements ICoder<Enum, Object > {


    @Override
    public Object encode(IDataBuffer buffer, String key, Enum value) {
        Object val;
        if(value instanceof IEnum){
            val =  ((IEnum) value).getValue();
        }else{
            val = value.name();
        }

        return val;
    }



    @Override
    public Enum decode(IDataBuffer buffer , Field field, Object value) {
        Class key = field.getType();
        Object o =value.toString();
        Enum[] enums = (Enum[]) key.getEnumConstants();
        if(IEnum.class.isAssignableFrom(key)){
            IEnum[] es = (IEnum[])enums;
            for (IEnum anEnum : es) {
                if(anEnum.getValue().toString().equals(o)){
                    return (Enum)anEnum;
                }
            }
        }else{
            for(Enum e:enums){
                if(e.name().equals(o)){
                    return  e;
                }
            }
        }
        return null;
    }




    @Override
    public boolean check(Class clazz) {
        return Enum.class.isAssignableFrom(clazz);
    }
}

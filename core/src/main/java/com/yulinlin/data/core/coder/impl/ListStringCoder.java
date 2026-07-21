package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.json.JsonUtil;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.util.ListString;

import java.lang.reflect.Field;


public class ListStringCoder implements ICoder<ListString,String> {

    @Override
    public String encode(IDataBuffer buffer, String key, ListString value) {
        return value.encode();
    }

    @Override
    public ListString decode(IDataBuffer buffer, Field field, Object value) {

        String string = value.toString();

        if(string.startsWith("[")){
            return JsonUtil.parseJson(string,ListString.class);
        }else {
            Class<?> aClass = GenericUtil.forField(field, 0);
            ListString list = new ListString();
            list.decode(value.toString(),aClass);
            return list;
        }

    }






}

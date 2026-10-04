package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.util.List;


public class StringCoder implements ICoder<String,String> {
    @Override
    public String encode(IDataBuffer buffer, String key, String value) {
        return value;
    }

    @Override
    public String decode(IDataBuffer buffer , Field field, Object value) {
        return value.toString();
    }

}

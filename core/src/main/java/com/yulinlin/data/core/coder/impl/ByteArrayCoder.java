package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.List;


public class ByteArrayCoder implements ICoder<byte[],String> {


    @Override
    public String encode(IDataBuffer buffer, String key, byte[] value) {
        byte[] vs =  Base64.getEncoder().encode(value);

        return new String(vs);
    }


    @Override
    public byte[] decode(IDataBuffer buffer , Field field, Object value) {
        if(value instanceof String){
            String str = (String) value;
            byte[] vs =  Base64.getDecoder().decode(str);
            return vs;
        }

        return (byte[])value;
    }



}

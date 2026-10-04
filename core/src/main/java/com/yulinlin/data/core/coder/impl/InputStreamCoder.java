package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.List;


public class InputStreamCoder implements ICoder<InputStream,String> {


    @SneakyThrows
    @Override
    public String encode(IDataBuffer buffer, String key, InputStream value) {
        InputStream inputStream =value;
        byte[] bs =  new byte[ inputStream.available()];
        inputStream.read(bs);

        byte[] vs =  Base64.getEncoder().encode(bs);

        return new String(vs);
    }

    @Override
    public InputStream decode(IDataBuffer buffer , Field field, Object value) {
        if(value instanceof String){
            String str = (String) value;
            byte[] vs =  Base64.getDecoder().decode(str);
            return new ByteArrayInputStream(vs);
        }
        return (InputStream)value;
    }




}

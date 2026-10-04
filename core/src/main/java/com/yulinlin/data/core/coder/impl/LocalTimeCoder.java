package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;


public class LocalTimeCoder implements ICoder<LocalTime, String> {

    DateTimeFormatter formatter =       DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public String encode(IDataBuffer buffer, String key, LocalTime value) {
        return formatter.format(value);
    }

    @Override
    public LocalTime decode(IDataBuffer buffer , Field field, Object value) {

        return LocalTime.parse(value.toString(),formatter);
    }



}

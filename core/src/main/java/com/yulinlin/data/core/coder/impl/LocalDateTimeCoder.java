package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class LocalDateTimeCoder implements ICoder<LocalDateTime, String> {

    DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String encode(IDataBuffer buffer, String key, LocalDateTime value) {
        return formatter.format(value);
    }

    @Override
    public LocalDateTime decode(IDataBuffer buffer , Field field, Object value) {
        return LocalDateTime.parse(value.toString(),formatter);



    }




}

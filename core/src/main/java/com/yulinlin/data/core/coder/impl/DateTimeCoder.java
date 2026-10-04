package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.util.DateTime;

import java.lang.reflect.Field;
import java.time.format.DateTimeFormatter;


public class DateTimeCoder implements ICoder<DateTime, String> {


    @Override
    public String encode(IDataBuffer buffer, String key, DateTime value) {
        return value.toString();
    }

    @Override
    public DateTime decode(IDataBuffer buffer , Field field, Object value) {

        return DateTime.parse(value.toString());

    }




}

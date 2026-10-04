package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;


public class LocalDateCoder implements ICoder<LocalDate, String> {

    DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("yyyy-MM-dd");


    @Override
    public String encode(IDataBuffer buffer, String key, LocalDate value) {
        return formatter.format(value);
    }

    @Override
    public LocalDate decode(IDataBuffer buffer , Field field, Object value) {

        return LocalDate.parse(value.toString(),formatter);
    }




}

package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.util.DateTime;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


public class DateCoder implements ICoder<Date, String> {



    @Override
    public String encode(IDataBuffer buffer, String key, Date value) {
        return DateTime.date(value).toString();
    }

    @Override
    public Date decode(IDataBuffer buffer , Field field, Object value) {
        return DateTime.parse(value.toString()).toDate();
    }





}

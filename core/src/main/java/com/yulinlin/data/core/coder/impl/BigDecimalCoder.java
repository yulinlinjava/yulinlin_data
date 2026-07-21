package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

public class BigDecimalCoder implements ICoder<BigDecimal,String> {

    @Override
    public String encode(IDataBuffer buffer, String key, BigDecimal value) {
        return value.toString();
    }

    @Override
    public BigDecimal decode(IDataBuffer buffer , Field field, Object value) {
        String stt  =  value.toString();
        return new BigDecimal(stt);
    }







}

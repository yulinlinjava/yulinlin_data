package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;

/**
 * 大数序列化
 */
public class BigIntegerCoder implements ICoder<BigInteger,String> {

    @Override
    public String encode(IDataBuffer buffer, String key, BigInteger value) {
        return value.toString();
    }

    @Override
    public BigInteger decode(IDataBuffer buffer , Field field, Object value) {
        String stt  =  value.toString();
        return new BigInteger(stt);
    }





}

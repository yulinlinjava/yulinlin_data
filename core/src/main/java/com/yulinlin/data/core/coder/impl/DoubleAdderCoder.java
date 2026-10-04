package com.yulinlin.data.core.coder.impl;


import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.atomic.DoubleAdder;


public class DoubleAdderCoder implements ICoder<DoubleAdder, Double > {
    @Override
    public Double encode(IDataBuffer buffer, String key, DoubleAdder value) {
        return value.doubleValue();
    }

    @Override
    public DoubleAdder decode(IDataBuffer buffer , Field field, Object value) {
        double l = new BigDecimal(value.toString()).doubleValue();
        DoubleAdder longAdder = new DoubleAdder();
        longAdder.add(l);
        return longAdder;
    }


}

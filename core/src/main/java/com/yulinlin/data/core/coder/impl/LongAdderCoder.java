package com.yulinlin.data.core.coder.impl;


import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;


public class LongAdderCoder implements ICoder<LongAdder, Long > {

    @Override
    public Long encode(IDataBuffer buffer, String key, LongAdder value) {
        return value.longValue();
    }

    @Override
    public LongAdder decode(IDataBuffer buffer , Field field, Object value) {
        long l = new BigDecimal(value.toString()).longValue();
        LongAdder longAdder = new LongAdder();
        longAdder.add(l);
        return longAdder;
    }


}

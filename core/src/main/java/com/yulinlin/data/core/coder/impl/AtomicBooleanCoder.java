package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

/** AtomicBoolean 是布尔维度，不作为计数值求和。 */
public final class AtomicBooleanCoder implements ICoder<AtomicBoolean, Boolean> {
    @Override public Boolean encode(IDataBuffer buffer, String key, AtomicBoolean value) {
        return value == null ? null : value.get();
    }
    @Override public AtomicBoolean decode(IDataBuffer buffer, Field field, Object value) {
        return decode(buffer, field.getType(), value);
    }
    @Override public AtomicBoolean decode(IDataBuffer buffer, Class type, Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if ("true".equalsIgnoreCase(text)) return new AtomicBoolean(true);
        if ("false".equalsIgnoreCase(text)) return new AtomicBoolean(false);
        return new AtomicBoolean(new BigDecimal(text).signum() != 0);
    }
}

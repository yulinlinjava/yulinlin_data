package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/** AtomicInteger 使用标量值编码，解码时检查范围，避免溢出截断。 */
public final class AtomicIntegerCoder implements ICoder<AtomicInteger, Integer> {
    @Override public Integer encode(IDataBuffer buffer, String key, AtomicInteger value) {
        return value == null ? null : value.get();
    }
    @Override public AtomicInteger decode(IDataBuffer buffer, Field field, Object value) {
        return decode(buffer, field.getType(), value);
    }
    @Override public AtomicInteger decode(IDataBuffer buffer, Class type, Object value) {
        return value == null ? null : new AtomicInteger(new BigDecimal(value.toString()).intValueExact());
    }
}

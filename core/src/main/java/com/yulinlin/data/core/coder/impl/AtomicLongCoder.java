package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/** AtomicLong 使用标量值编码，解码时检查范围，避免溢出截断。 */
public final class AtomicLongCoder implements ICoder<AtomicLong, Long> {
    @Override public Long encode(IDataBuffer buffer, String key, AtomicLong value) {
        return value == null ? null : value.get();
    }
    @Override public AtomicLong decode(IDataBuffer buffer, Field field, Object value) {
        return decode(buffer, field.getType(), value);
    }
    @Override public AtomicLong decode(IDataBuffer buffer, Class type, Object value) {
        return value == null ? null : new AtomicLong(new BigDecimal(value.toString()).longValueExact());
    }
}

package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import java.lang.reflect.Field;
import java.math.BigDecimal;

/** Byte 使用标量值编码，解码时检查范围，避免溢出截断。 */
public final class ByteCoder implements ICoder<Byte, Byte> {
    @Override public Byte encode(IDataBuffer buffer, String key, Byte value) {
        return value == null ? null : value;
    }
    @Override public Byte decode(IDataBuffer buffer, Field field, Object value) {
        return decode(buffer, field.getType(), value);
    }
    @Override public Byte decode(IDataBuffer buffer, Class type, Object value) {
        return value == null ? null : new BigDecimal(value.toString()).byteValueExact();
    }
    @Override public boolean check(Class type) {
        return type == Byte.class || type == byte.class;
    }
}

package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import java.lang.reflect.Field;
import java.math.BigDecimal;

/** Short 使用标量值编码，解码时检查范围，避免溢出截断。 */
public final class ShortCoder implements ICoder<Short, Short> {
    @Override public Short encode(IDataBuffer buffer, String key, Short value) {
        return value == null ? null : value;
    }
    @Override public Short decode(IDataBuffer buffer, Field field, Object value) {
        return decode(buffer, field.getType(), value);
    }
    @Override public Short decode(IDataBuffer buffer, Class type, Object value) {
        return value == null ? null : new BigDecimal(value.toString()).shortValueExact();
    }
    @Override public boolean check(Class type) {
        return type == Short.class || type == short.class;
    }
}

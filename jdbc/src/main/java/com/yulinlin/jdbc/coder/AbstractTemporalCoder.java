package com.yulinlin.jdbc.coder;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.util.Objects;

/** 只复用类型声明、空值处理和 decode 入口；数据库存储规则由子类实现。 */
public abstract class AbstractTemporalCoder<T, V> implements ICoder<T, V> {
    private final Class<T> type;

    protected AbstractTemporalCoder(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override public final Class<T> getTypeClass() { return type; }

    @Override public final V encode(IDataBuffer buffer, String key, T value) {
        return value == null ? null : encodeValue(value);
    }

    @Override public final T decode(IDataBuffer buffer, Field field, Object value) {
        return value == null ? null : decodeValue(value);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public final T decode(IDataBuffer buffer, Class field, Object value) {
        return value == null ? null : decodeValue(value);
    }

    protected abstract V encodeValue(T value);

    protected abstract T decodeValue(Object value);
}

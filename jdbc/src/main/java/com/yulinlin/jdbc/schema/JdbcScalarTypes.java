package com.yulinlin.jdbc.schema;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/** 与标量编码器对应的存储类型；数据库方言仍由各 SchemaManager 决定。 */
public final class JdbcScalarTypes {
    private JdbcScalarTypes() { }

    public static Class<?> storageType(Class<?> type) {
        if (type == AtomicInteger.class) return Integer.class;
        if (type == AtomicLong.class || type == LongAdder.class) return Long.class;
        if (type == DoubleAdder.class) return Double.class;
        if (type == AtomicBoolean.class) return Boolean.class;
        return type;
    }
}

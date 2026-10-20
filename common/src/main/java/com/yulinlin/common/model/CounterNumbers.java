package com.yulinlin.common.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/** 更新和插入共用的不可变数字快照，不对大数做 long/double 降精度转换。 */
final class CounterNumbers {
    private CounterNumbers() { }

    static BigDecimal snapshot(Number value) {
        Class<?> type = value.getClass();
        if (type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == AtomicInteger.class || type == AtomicLong.class || type == LongAdder.class)
            return BigDecimal.valueOf(value.longValue());
        if (type == BigInteger.class) return new BigDecimal((BigInteger) value);
        if (type == BigDecimal.class) return (BigDecimal) value;
        if (type == Float.class || type == Double.class || type == DoubleAdder.class) {
            double snapshot = value.doubleValue();
            if (!Double.isFinite(snapshot)) throw new IllegalArgumentException("统计数值不能为 NaN 或无穷大");
            return type == Float.class ? new BigDecimal(Float.toString((float) snapshot))
                    : BigDecimal.valueOf(snapshot);
        }
        throw new IllegalArgumentException("不支持的统计数字类型：" + type.getName());
    }

    /** 能精确表示为 long 时保持普通整数参数，否则使用 JDBC 支持的 BigDecimal。 */
    static Number parameter(BigDecimal value) {
        try { return value.longValueExact(); }
        catch (ArithmeticException ignored) { return value; }
    }
}

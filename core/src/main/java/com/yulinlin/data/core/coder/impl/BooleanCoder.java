package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;

/** 布尔转换：保留原生布尔值，数字零为 false、非零为 true，非法值明确拒绝。 */
public class BooleanCoder implements ICoder<Boolean, Boolean> {

    @Override
    public Boolean encode(IDataBuffer buffer, String key, Boolean value) {
        return value;
    }

    @Override
    public Boolean decode(IDataBuffer buffer, Field field, Object value) {
        return decodeValue(value);
    }

    @Override
    public Boolean decode(IDataBuffer buffer, Class type, Object value) {
        return decodeValue(value);
    }

    /** 与 AtomicBooleanCoder 共用，避免不同入口对 false、负数、小数产生不同结果。 */
    static Boolean decodeValue(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof BigDecimal number) return number.signum() != 0;
        if (value instanceof BigInteger number) return number.signum() != 0;
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue() != 0;
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) throw invalidValue(value);
            return number != 0;
        }
        if (value instanceof CharSequence || value instanceof Character || value instanceof Number) {
            String text = value.toString().trim();
            if ("true".equalsIgnoreCase(text) || "t".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text) || "f".equalsIgnoreCase(text)) return false;
            if ("1".equals(text)) return true;
            if ("0".equals(text)) return false;
            try {
                return new BigDecimal(text).signum() != 0;
            } catch (NumberFormatException error) {
                throw invalidValue(value);
            }
        }
        throw invalidValue(value);
    }

    private static IllegalArgumentException invalidValue(Object value) {
        // 不打印原值，避免将业务敏感数据带入异常日志。
        return new IllegalArgumentException("无法转换为布尔值：类型=" + value.getClass().getName()
                + "；仅支持布尔值、有限数字或 true/false、t/f、数字文本");
    }

    @Override
    public boolean check(Class clazz) {
        return ICoder.super.check(clazz) || clazz == boolean.class;
    }
}

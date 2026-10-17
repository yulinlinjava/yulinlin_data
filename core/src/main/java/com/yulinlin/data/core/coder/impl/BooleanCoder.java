package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Locale;


public class BooleanCoder implements ICoder<Boolean,Boolean> {

    @Override
    public Boolean encode(IDataBuffer buffer, String key, Boolean value) {
        return value;
    }

    @Override
    public Boolean decode(IDataBuffer buffer , Field field, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).compareTo(BigDecimal.ZERO) != 0;
        }
        if (value instanceof byte[] bytes && bytes.length == 1) {
            return bytes[0] != 0;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "1", "t", "yes", "y", "on" -> true;
            case "false", "0", "f", "no", "n", "off", "" -> false;
            default -> {
                try {
                    yield new BigDecimal(text).compareTo(BigDecimal.ZERO) != 0;
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Unsupported boolean value: " + value, error);
                }
            }
        };
    }




    @Override
    public boolean check(Class clazz) {
        return ICoder.super.check(clazz) || clazz == boolean.class;
    }
}

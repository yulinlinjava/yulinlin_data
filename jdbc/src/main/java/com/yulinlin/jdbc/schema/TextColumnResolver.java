package com.yulinlin.jdbc.schema;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.TextTypeEnum;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Validates portable text-column options shared by JDBC schema managers. */
public final class TextColumnResolver {
    public static final int MAX_DESCRIPTION_CODE_POINTS = 1024;

    public record Definition(TextTypeEnum type, int length, boolean explicitLength, String description) { }

    private TextColumnResolver() { }

    public static Definition resolve(Field field, JoinField mapping) {
        if (mapping == null) return new Definition(TextTypeEnum.auto, 0, false, "");
        int length = mapping.textLength();
        if (length < 0) {
            throw invalid(field, "textLength must be zero or positive");
        }
        TextTypeEnum requested = mapping.textType();
        if (requested == TextTypeEnum.text && length > 0) {
            throw invalid(field, "textLength cannot be used with textType=text");
        }
        if ((requested != TextTypeEnum.auto || length > 0) && !usesTextStorage(field.getType())) {
            throw invalid(field, "textType/textLength can only be used with text-encoded fields");
        }
        String description = mapping.description();
        if (description.indexOf('\0') >= 0) {
            throw invalid(field, "description cannot contain NUL");
        }
        if (description.codePointCount(0, description.length()) > MAX_DESCRIPTION_CODE_POINTS) {
            throw invalid(field, "description cannot exceed " + MAX_DESCRIPTION_CODE_POINTS + " characters");
        }
        TextTypeEnum effective = requested == TextTypeEnum.auto && length > 0
                ? TextTypeEnum.varchar : requested;
        return new Definition(effective, length, length > 0, description);
    }

    private static boolean usesTextStorage(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return false;
        }
        // BigDecimal/BigInteger, dates, enums, byte arrays and JSON objects use the shared text codecs.
        return type == BigDecimal.class || type == BigInteger.class || !Number.class.isAssignableFrom(type);
    }

    private static IllegalArgumentException invalid(Field field, String message) {
        return new IllegalArgumentException("Invalid text column " + field.getDeclaringClass().getName()
                + "." + field.getName() + ": " + message);
    }
}

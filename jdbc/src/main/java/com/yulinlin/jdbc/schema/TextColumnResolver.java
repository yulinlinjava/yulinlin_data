package com.yulinlin.jdbc.schema;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.TextTypeEnum;
import com.yulinlin.data.lang.util.DateTime;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

/** Validates portable text-column options shared by JDBC schema managers. */
public final class TextColumnResolver {
    /** 仅用于未显式指定 textLength 的字符串主键，不是长度上限。 */
    public static final int DEFAULT_PRIMARY_KEY_LENGTH = 32;
    public static final int MAX_DESCRIPTION_CODE_POINTS = 1024;

    public record Definition(TextTypeEnum type, int length, boolean explicitLength, String description) { }

    private TextColumnResolver() { }

    public static Definition resolve(Field field, JoinField mapping) {
        if (mapping == null) return new Definition(TextTypeEnum.auto, 0, false, "");
        int length = mapping.textLength();
        if (length < 0) {
            throw invalid(field, "textLength 不能为负数；请设为 0 使用默认值或指定正整数");
        }
        TextTypeEnum requested = mapping.textType();
        if (requested == TextTypeEnum.text && length > 0) {
            throw invalid(field, "textType=text 不能同时指定 textLength；请移除长度或改为 varchar");
        }
        if ((requested != TextTypeEnum.auto || length > 0) && !usesTextStorage(field.getType())) {
            throw invalid(field, "textType/textLength 只适用于文本存储字段；请检查 Java 字段类型并移除不适用的配置");
        }
        String description = mapping.description();
        if (description.indexOf('\0') >= 0) {
            throw invalid(field, "description 包含 NUL 字符；请移除该控制字符");
        }
        if (description.codePointCount(0, description.length()) > MAX_DESCRIPTION_CODE_POINTS) {
            throw invalid(field, "description 长度超限；请缩短为不超过 " + MAX_DESCRIPTION_CODE_POINTS + " 个字符");
        }
        TextTypeEnum effective = requested == TextTypeEnum.auto && length > 0
                ? TextTypeEnum.varchar : requested;
        return new Definition(effective, length, length > 0, description);
    }

    private static boolean usesTextStorage(Class<?> type) {
        type = JdbcScalarTypes.storageType(type);
        if (type == boolean.class || type == Boolean.class) return false;
        if (Date.class.isAssignableFrom(type) || type == DateTime.class || type == LocalDate.class
                || type == LocalDateTime.class || type == LocalTime.class) return false;
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return false;
        }
        // BigDecimal/BigInteger, enums, byte arrays and JSON objects use the shared text codecs.
        return type == BigDecimal.class || type == BigInteger.class || !Number.class.isAssignableFrom(type);
    }

    private static IllegalArgumentException invalid(Field field, String message) {
        return new IllegalArgumentException("文本列注解配置错误，字段：" + field.getDeclaringClass().getName()
                + "." + field.getName() + ": " + message);
    }
}

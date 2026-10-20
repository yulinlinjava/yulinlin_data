package com.yulinlin.jdbc.coder;

import com.yulinlin.data.lang.util.DateTime;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Locale;

/** 无状态的值转换工具；不注册编码器，也不决定数据库绑定类型或存储格式。 */
public final class TemporalConversions {
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-M-d H:m:s")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private TemporalConversions() { }

    /** 本地时间文本直接解析，不经 Timestamp 中转；兼容旧驱动已经返回的 Timestamp。 */
    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime local) return local;
        if (value instanceof DateTime date) return date.toLocalDateTime();
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace('T', ' '), LOCAL_DATE_TIME_FORMAT);
    }

    public static DateTime toDateTime(Object value) {
        return value == null ? null : DateTime.date(toLocalDateTime(value));
    }

    /** Date 表示时间点；从无时区时间读取时保留原有 JVM 本地时区转换语义。 */
    public static Date toDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return new Date(date.getTime());
        return new Date(Timestamp.valueOf(toLocalDateTime(value)).getTime());
    }

    public static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    public static LocalTime toLocalTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalTime time) return time;
        if (value instanceof Time time) return time.toLocalTime();
        return LocalTime.parse(value.toString());
    }
}

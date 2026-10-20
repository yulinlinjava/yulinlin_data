package com.yulinlin.jdbc.sqlite.coder;

import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.coder.AbstractTemporalCoder;
import com.yulinlin.jdbc.coder.TemporalConversions;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/** SQLite 自己的 TEXT 时间编解码器；固定格式、精度和绑定兜底均由本模块维护。 */
public final class SqliteTemporalCoders {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private static final DateCoder DATE = new DateCoder();
    private static final DateTimeCoder DATE_TIME = new DateTimeCoder();
    private static final LocalDateCoder LOCAL_DATE = new LocalDateCoder();
    private static final LocalDateTimeCoder LOCAL_DATE_TIME = new LocalDateTimeCoder();
    private static final LocalTimeCoder LOCAL_TIME = new LocalTimeCoder();

    private SqliteTemporalCoders() { }

    /** 原始 SQL 参数的兜底也调用本模块编码器，避免与实体字段编码产生格式差异。 */
    public static Object toTextValue(Object value) {
        if (value instanceof DateTime date) return DATE_TIME.encode(null, null, date);
        if (value instanceof LocalDateTime date) return LOCAL_DATE_TIME.encode(null, null, date);
        if (value instanceof LocalDate date) return LOCAL_DATE.encode(null, null, date);
        if (value instanceof LocalTime time) return LOCAL_TIME.encode(null, null, time);
        if (value instanceof Timestamp timestamp) return LOCAL_DATE_TIME.encode(null, null, timestamp.toLocalDateTime());
        if (value instanceof java.sql.Date date) return LOCAL_DATE.encode(null, null, date.toLocalDate());
        if (value instanceof Time time) return LOCAL_TIME.encode(null, null, time.toLocalTime());
        if (value instanceof Date date) return DATE.encode(null, null, date);
        return value;
    }

    /** Date 是时间点，保留按 JVM 时区转换为 SQLite 本地时间文本的规则。 */
    public static final class DateCoder extends AbstractTemporalCoder<Date, String> {
        public DateCoder() { super(Date.class); }

        @Override protected String encodeValue(Date value) {
            return DATE_TIME_FORMAT.format(new Timestamp(value.getTime()).toLocalDateTime());
        }
        @Override protected Date decodeValue(Object value) { return TemporalConversions.toDate(value); }
    }

    public static final class DateTimeCoder extends AbstractTemporalCoder<DateTime, String> {
        public DateTimeCoder() { super(DateTime.class); }

        @Override protected String encodeValue(DateTime value) {
            return DATE_TIME_FORMAT.format(value.toLocalDateTime());
        }
        @Override protected DateTime decodeValue(Object value) { return TemporalConversions.toDateTime(value); }
    }

    public static final class LocalDateCoder extends AbstractTemporalCoder<LocalDate, String> {
        public LocalDateCoder() { super(LocalDate.class); }

        @Override protected String encodeValue(LocalDate value) { return value.toString(); }
        @Override protected LocalDate decodeValue(Object value) { return TemporalConversions.toLocalDate(value); }
    }

    public static final class LocalDateTimeCoder extends AbstractTemporalCoder<LocalDateTime, String> {
        public LocalDateTimeCoder() { super(LocalDateTime.class); }

        @Override protected String encodeValue(LocalDateTime value) { return DATE_TIME_FORMAT.format(value); }
        @Override protected LocalDateTime decodeValue(Object value) { return TemporalConversions.toLocalDateTime(value); }
    }

    public static final class LocalTimeCoder extends AbstractTemporalCoder<LocalTime, String> {
        public LocalTimeCoder() { super(LocalTime.class); }

        @Override protected String encodeValue(LocalTime value) { return TIME_FORMAT.format(value); }
        @Override protected LocalTime decodeValue(Object value) { return TemporalConversions.toLocalTime(value); }
    }
}

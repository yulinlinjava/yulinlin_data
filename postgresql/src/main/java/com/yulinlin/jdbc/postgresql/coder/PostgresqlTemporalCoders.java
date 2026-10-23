package com.yulinlin.jdbc.postgresql.coder;

import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.coder.AbstractTemporalCoder;
import com.yulinlin.jdbc.coder.TemporalConversions;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

/** PostgreSQL 自己的时间编解码器；只复用值转换工具，不引用其他数据库的编码器。 */
public final class PostgresqlTemporalCoders {

    private PostgresqlTemporalCoders() { }

    /** 对应 TIMESTAMP WITHOUT TIME ZONE：按 JVM 本地时区提取数字，与解码对称。 */
    public static final class DateCoder extends AbstractTemporalCoder<Date, LocalDateTime> {
        public DateCoder() { super(Date.class); }

        @Override protected LocalDateTime encodeValue(Date value) {
            return new Timestamp(value.getTime()).toLocalDateTime();
        }
        @Override protected Date decodeValue(Object value) { return TemporalConversions.toDate(value); }
    }

    /** 对应 TIMESTAMP WITHOUT TIME ZONE，不转 Timestamp，避免引入时间点换算。 */
    public static final class DateTimeCoder extends AbstractTemporalCoder<DateTime, LocalDateTime> {
        public DateTimeCoder() { super(DateTime.class); }

        @Override protected LocalDateTime encodeValue(DateTime value) { return value.toLocalDateTime(); }
        @Override protected DateTime decodeValue(Object value) { return TemporalConversions.toDateTime(value); }
    }

    public static final class LocalDateCoder extends AbstractTemporalCoder<LocalDate, java.sql.Date> {
        public LocalDateCoder() { super(LocalDate.class); }

        @Override protected java.sql.Date encodeValue(LocalDate value) { return java.sql.Date.valueOf(value); }
        @Override protected LocalDate decodeValue(Object value) { return TemporalConversions.toLocalDate(value); }
    }

    public static final class LocalDateTimeCoder extends AbstractTemporalCoder<LocalDateTime, LocalDateTime> {
        public LocalDateTimeCoder() { super(LocalDateTime.class); }

        @Override protected LocalDateTime encodeValue(LocalDateTime value) { return value; }
        @Override protected LocalDateTime decodeValue(Object value) { return TemporalConversions.toLocalDateTime(value); }
    }

    /** 保留原有 java.sql.Time 的秒级精度。 */
    public static final class LocalTimeCoder extends AbstractTemporalCoder<LocalTime, Time> {
        public LocalTimeCoder() { super(LocalTime.class); }

        @Override protected Time encodeValue(LocalTime value) { return Time.valueOf(value); }
        @Override protected LocalTime decodeValue(Object value) { return TemporalConversions.toLocalTime(value); }
    }
}

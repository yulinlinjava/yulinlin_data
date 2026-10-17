package com.yulinlin.jdbc.coder;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.MapCoderManager;
import com.yulinlin.data.lang.util.DateTime;

import java.lang.reflect.Field;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;

public class JdbcCoderManager extends MapCoderManager {

    private static final Map<Class<?>, ICoder<?, ?>> TEMPORAL_CODERS = Map.of(
            Date.class, new JdbcDateCoder(),
            DateTime.class, new JdbcDateTimeCoder(),
            LocalDate.class, new JdbcLocalDateCoder(),
            LocalDateTime.class, new JdbcLocalDateTimeCoder(),
            LocalTime.class, new JdbcLocalTimeCoder()
    );


    public JdbcCoderManager() {
        super();

    }

    protected void init(){
        super.init();
        this.registerCoder(new CollectionCoder());

        this.registerCoder(new MapCoder());
        this.registerCoder(new ObjectCoder());

    }

    /** JDBC drivers receive typed temporal parameters instead of VARCHAR values. */
    @Override
    @SuppressWarnings("rawtypes")
    public ICoder getCoder(Class clazz) {
        ICoder<?, ?> coder = TEMPORAL_CODERS.get(clazz);
        return coder == null ? super.getCoder(clazz) : coder;
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime local) return local;
        return Timestamp.valueOf(value.toString().replace('T', ' ')).toLocalDateTime();
    }

    private static final class JdbcDateCoder implements ICoder<Date, Timestamp> {
        @Override public Timestamp encode(IDataBuffer buffer, String key, Date value) {
            return new Timestamp(value.getTime());
        }
        @Override public Date decode(IDataBuffer buffer, Field field, Object value) {
            if (value instanceof Date date) return new Date(date.getTime());
            return new Date(Timestamp.valueOf(value.toString().replace('T', ' ')).getTime());
        }
    }

    private static final class JdbcDateTimeCoder implements ICoder<DateTime, Timestamp> {
        @Override public Timestamp encode(IDataBuffer buffer, String key, DateTime value) {
            return Timestamp.valueOf(value.toLocalDateTime());
        }
        @Override public DateTime decode(IDataBuffer buffer, Field field, Object value) {
            return DateTime.date(localDateTime(value));
        }
    }

    private static final class JdbcLocalDateCoder implements ICoder<LocalDate, java.sql.Date> {
        @Override public java.sql.Date encode(IDataBuffer buffer, String key, LocalDate value) {
            return java.sql.Date.valueOf(value);
        }
        @Override public LocalDate decode(IDataBuffer buffer, Field field, Object value) {
            if (value instanceof java.sql.Date date) return date.toLocalDate();
            if (value instanceof LocalDate date) return date;
            return LocalDate.parse(value.toString());
        }
    }

    private static final class JdbcLocalDateTimeCoder implements ICoder<LocalDateTime, Timestamp> {
        @Override public Timestamp encode(IDataBuffer buffer, String key, LocalDateTime value) {
            return Timestamp.valueOf(value);
        }
        @Override public LocalDateTime decode(IDataBuffer buffer, Field field, Object value) {
            return localDateTime(value);
        }
    }

    private static final class JdbcLocalTimeCoder implements ICoder<LocalTime, Time> {
        @Override public Time encode(IDataBuffer buffer, String key, LocalTime value) {
            return Time.valueOf(value);
        }
        @Override public LocalTime decode(IDataBuffer buffer, Field field, Object value) {
            if (value instanceof Time time) return time.toLocalTime();
            if (value instanceof LocalTime time) return time;
            return LocalTime.parse(value.toString());
        }
    }



}

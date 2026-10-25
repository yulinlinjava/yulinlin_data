package com.yulinlin.jdbc.h2.coder;

import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.lang.util.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("java.util.TimeZone.default")
class H2DateCoderTest {
    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreTimeZone() { TimeZone.setDefault(originalZone); }

    @ParameterizedTest
    @CsvSource({"Asia/Shanghai,UTC", "Asia/Shanghai,+09:00", "Asia/Shanghai,-05:00",
            "UTC,UTC", "UTC,+09:00", "UTC,-05:00",
            "America/New_York,UTC", "America/New_York,+09:00", "America/New_York,-05:00"})
    void timestampWithoutTimeZoneKeepsDateAndDateTimeNumbers(String jvmZone, String databaseZone) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
        var local = LocalDateTime.of(2026, 10, 23, 21, 12, 38, 123_000_000);
        var precise = local.withNano(123_456_000);
        Date date = DateTime.date(local).toDate();
        var coder = new H2TemporalCoders.DateCoder();
        var manager = new H2CoderManager();
        var values = manager.createEncoderBuffer();
        values.put("payTime", date);
        values.put("crtTime", DateTime.date(date));
        values.put("preciseTime", DateTime.date(precise));
        assertEquals(local, values.toMap().get("payTime"));
        assertEquals(local, values.toMap().get("crtTime"));
        assertEquals(precise, values.toMap().get("preciseTime"));
        var context = new SimpParamsContext(RequestType.select, Map.of(), values, Object.class, true);
        String dateKey = context.putGetKey(date);
        String dateTimeKey = context.putGetKey(DateTime.date(local));
        Object dateParameter = values.toMap().get(dateKey.substring(2, dateKey.length() - 1));
        Object dateTimeParameter = values.toMap().get(dateTimeKey.substring(2, dateTimeKey.length() - 1));
        assertEquals(local, dateParameter);
        assertEquals(local, dateTimeParameter);
        assertNull(coder.encode(null, null, null));
        assertNull(coder.decode(null, Date.class, null));

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:date_" + UUID.randomUUID());
             var statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE '" + databaseZone + "'");
            statement.execute("CREATE TABLE dates (pay_time TIMESTAMP(3), crt_time TIMESTAMP(3), precise_time TIMESTAMP(6))");
            try (var insert = connection.prepareStatement("INSERT INTO dates VALUES (?, ?, ?)")) {
                insert.setObject(1, values.toMap().get("payTime"));
                insert.setObject(2, values.toMap().get("crtTime"));
                insert.setObject(3, values.toMap().get("preciseTime"));
                insert.addBatch();
                assertArrayEquals(new int[]{1}, insert.executeBatch());
            }
            // Read and filter the same stored numbers after changing the connection time zone.
            statement.execute("SET TIME ZONE '" + (databaseZone.equals("UTC") ? "+09:00" : "UTC") + "'");
            try (var query = connection.prepareStatement("SELECT * FROM dates WHERE pay_time = ? AND crt_time = ?")) {
                query.setObject(1, dateParameter);
                query.setObject(2, dateTimeParameter);
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(local, rows.getObject("pay_time", LocalDateTime.class));
                    assertEquals("2026-10-23 21:12:38.123", rows.getString("pay_time"));
                    assertEquals(rows.getString("pay_time"), rows.getString("crt_time"));
                    assertEquals(date, coder.decode(null, Date.class, rows.getString("pay_time")));
                    assertEquals(local, new H2TemporalCoders.DateTimeCoder()
                            .decode(null, DateTime.class, rows.getString("crt_time")).toLocalDateTime());
                    assertEquals(precise, new H2TemporalCoders.DateTimeCoder()
                            .decode(null, DateTime.class, rows.getString("precise_time")).toLocalDateTime());
                    assertFalse(rows.next());
                }
            }
        }
    }
}

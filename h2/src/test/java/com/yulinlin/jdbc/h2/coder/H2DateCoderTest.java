package com.yulinlin.jdbc.h2.coder;

import com.yulinlin.data.lang.util.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("java.util.TimeZone.default")
class H2DateCoderTest {
    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreTimeZone() { TimeZone.setDefault(originalZone); }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "+09:00", "-05:00"})
    void timestampWithoutTimeZoneKeepsDateAndDateTimeNumbers(String databaseZone) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        var local = LocalDateTime.of(2026, 10, 23, 21, 12, 38, 123_000_000);
        Date date = DateTime.date(local).toDate();
        var coder = new H2TemporalCoders.DateCoder();
        var manager = new H2CoderManager();
        var values = manager.createEncoderBuffer();
        values.put("payTime", date);
        values.put("crtTime", DateTime.date(date));
        assertEquals(local, values.toMap().get("payTime"));
        assertNull(coder.encode(null, null, null));
        assertNull(coder.decode(null, Date.class, null));

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:date_" + UUID.randomUUID());
             var statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE '" + databaseZone + "'");
            statement.execute("CREATE TABLE dates (pay_time TIMESTAMP(3), crt_time TIMESTAMP(3))");
            try (var insert = connection.prepareStatement("INSERT INTO dates VALUES (?, ?)")) {
                insert.setObject(1, values.toMap().get("payTime"));
                insert.setObject(2, values.toMap().get("crtTime"));
                insert.addBatch();
                assertArrayEquals(new int[]{1}, insert.executeBatch());
            }
            try (var query = connection.prepareStatement("SELECT * FROM dates WHERE pay_time = ?")) {
                query.setObject(1, values.toMap().get("payTime"));
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(local, rows.getObject("pay_time", LocalDateTime.class));
                    assertEquals("2026-10-23 21:12:38.123", rows.getString("pay_time"));
                    assertEquals(rows.getString("pay_time"), rows.getString("crt_time"));
                    assertEquals(date, coder.decode(null, Date.class, rows.getString("pay_time")));
                    assertFalse(rows.next());
                }
            }
        }
    }
}

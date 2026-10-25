package com.yulinlin.jdbc.postgresql.coder;

import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.lang.util.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("java.util.TimeZone.default")
class PostgresqlDateCoderTest {
    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreTimeZone() { TimeZone.setDefault(originalZone); }

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Shanghai", "UTC", "America/New_York"})
    void dateUsesLocalDateTimeAndDecodesSymmetrically(String zone) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        var local = LocalDateTime.of(2026, 10, 23, 21, 12, 38, 123_000_000);
        Date date = DateTime.date(local).toDate();
        var coder = new PostgresqlTemporalCoders.DateCoder();
        var buffer = new PostgresqlCoderManager().createEncoderBuffer();
        buffer.put("payTime", date);
        buffer.put("crtTime", DateTime.date(date));
        assertEquals(local, buffer.toMap().get("payTime"));
        assertEquals(buffer.toMap().get("payTime"), buffer.toMap().get("crtTime"));
        var context = new SimpParamsContext(RequestType.select, Map.of(), buffer, Object.class, true);
        for (Object value : new Object[]{date, DateTime.date(local)}) {
            String key = context.putGetKey(value);
            assertEquals(local, buffer.toMap().get(key.substring(2, key.length() - 1)));
        }
        var precise = local.withNano(123_456_789);
        buffer.put("preciseTime", DateTime.date(precise));
        assertEquals(precise, buffer.toMap().get("preciseTime"), "Do not truncate DateTime to Date milliseconds");
        assertEquals(precise, new PostgresqlTemporalCoders.DateTimeCoder()
                .decode(null, DateTime.class, precise.toString()).toLocalDateTime());
        assertEquals(date, coder.decode(null, Date.class, local));
        assertEquals(date, coder.decode(null, Entity.class.getDeclaredField("payTime"),
                "2026-10-23 21:12:38.123"));
        assertNull(coder.encode(null, null, null));
        assertNull(coder.decode(null, Date.class, null));
    }

    private static final class Entity { private Date payTime; }
}

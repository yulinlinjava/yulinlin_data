package com.yulinlin.jdbc.log;

import com.yulinlin.data.lang.util.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("java.util.TimeZone.default")
class JdbcExecutionLogTest {
    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreTimeZone() { TimeZone.setDefault(originalZone); }

    @Test
    void formatsTheReportedParametersWithoutTOrFractionalSeconds() {
        var millis = LocalDateTime.parse("2026-10-23T21:37:56.030");
        var nanos = LocalDateTime.parse("2026-10-23T21:37:56.074906900");
        var params = List.of(millis, nanos, 2113625973767131136L, nanos);
        assertEquals("[2026-10-23 21:37:56, 2026-10-23 21:37:56, 2113625973767131136, 2026-10-23 21:37:56]",
                JdbcExecutionLog.formatParams(params, 128));
        assertSame(millis, params.get(0));
        assertSame(nanos, params.get(1));
        assertEquals(30_000_000, millis.getNano());
        assertEquals(74_906_900, nanos.getNano());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Shanghai", "UTC", "America/New_York"})
    void keepsLocalNumbersAndUsesTheJvmZoneOnlyForDate(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        var local = LocalDateTime.of(2026, 10, 23, 21, 37, 56, 999_999_999);
        var timestamp = Timestamp.valueOf(local);
        Date date = new Date(timestamp.getTime());
        long epoch = date.getTime();
        var dateTime = DateTime.date(local);
        assertEquals("[2026-10-23 21:37:56, 2026-10-23 21:37:56, 2026-10-23 21:37:56, 2026-10-23 21:37:56]",
                JdbcExecutionLog.formatParams(List.of(local, timestamp, date, dateTime), 128));
        assertEquals(epoch, date.getTime());
        assertEquals(999_999_999, timestamp.getNanos());
        assertEquals(local, dateTime.toLocalDateTime());
    }

    @Test
    void midnightStillShowsHoursMinutesAndSeconds() {
        assertEquals("[2026-10-23 00:00:00]", JdbcExecutionLog.formatParams(
                List.of(LocalDate.of(2026, 10, 23).atStartOfDay()), 128));
    }

    @Test
    void doesNotParseStringsOrAddDateAndTimePartsToOtherTypes() {
        assertEquals("[2026-10-23T21:37:56.030, 2026-10-23, 21:37:56, 2026-10-23, 21:37:56, null, 42]",
                JdbcExecutionLog.formatParams(Arrays.asList("2026-10-23T21:37:56.030",
                        java.sql.Date.valueOf("2026-10-23"), Time.valueOf("21:37:56"),
                        LocalDate.of(2026, 10, 23), LocalTime.of(21, 37, 56), null, 42), 128));
    }

    @Test
    void keepsExistingLengthLimitsAndArraySummaries() {
        var local = LocalDateTime.of(2026, 10, 23, 21, 37, 56);
        assertEquals("[2026-10...]", JdbcExecutionLog.formatParams(List.of(local), 10));
        assertEquals("[<byte[] length=3>]", JdbcExecutionLog.formatParams(List.of(new byte[3]), 128));
        assertTrue(JdbcExecutionLog.formatParams(IntStream.range(0, 21).mapToObj(i -> local).toList(), 128)
                .endsWith(", ...(+1)]"));
    }
}

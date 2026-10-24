package com.yulinlin.jdbc.postgresql.coder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Time;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class PostgresqlLocalTimeCoderTest {
    @ParameterizedTest
    @ValueSource(strings = {"00:00:00", "13:14:15.123456", "23:59:59.999999", "13:14:15.123456789"})
    void encodingDoesNotTruncateFractionsBeforeTheDriver(String text) {
        var value = LocalTime.parse(text);
        var coder = new PostgresqlTemporalCoders.LocalTimeCoder();
        var buffer = new PostgresqlCoderManager().createEncoderBuffer();
        buffer.put("time", value);
        assertEquals(value, buffer.toMap().get("time"));
        assertEquals(value, coder.decode(null, LocalTime.class, text));
        assertEquals(value, coder.decode(null, LocalTime.class, value));
    }

    @Test
    void retainsLegacyTimeDecodingAndNullHandling() {
        var coder = new PostgresqlTemporalCoders.LocalTimeCoder();
        assertEquals(LocalTime.of(13, 14, 15), coder.decode(null, LocalTime.class, Time.valueOf("13:14:15")));
        assertNull(coder.encode(null, null, null));
        assertNull(coder.decode(null, LocalTime.class, null));
    }
}

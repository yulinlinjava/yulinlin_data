package com.yulinlin.jdbc.postgresql.coder;

import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PostgresqlLocalDateCoderTest {
    @ParameterizedTest
    @ValueSource(strings = {"2000-02-29", "1970-01-01", "2026-12-31"})
    void fieldsAndConditionsKeepNativeDateAndDecodeLegacyValues(String text) throws Exception {
        var value = LocalDate.parse(text);
        var buffer = new PostgresqlCoderManager().createEncoderBuffer();
        buffer.put("birthday", value);
        assertEquals(value, buffer.toMap().get("birthday"));
        var context = new SimpParamsContext(RequestType.select, Map.of(), buffer, Object.class, true);
        String key = context.putGetKey(value);
        assertEquals(value, buffer.toMap().get(key.substring(2, key.length() - 1)));
        var coder = new PostgresqlTemporalCoders.LocalDateCoder();
        assertEquals(value, coder.decode(null, LocalDate.class, value));
        assertEquals(value, coder.decode(null, Entity.class.getDeclaredField("birthday"), text));
        assertEquals(value, coder.decode(null, LocalDate.class, java.sql.Date.valueOf(value)));
    }

    @Test
    void nullsRemainNull() {
        var coder = new PostgresqlTemporalCoders.LocalDateCoder();
        assertNull(coder.encode(null, null, null));
        assertNull(coder.decode(null, LocalDate.class, null));
    }

    private static class Entity { private LocalDate birthday; }
}

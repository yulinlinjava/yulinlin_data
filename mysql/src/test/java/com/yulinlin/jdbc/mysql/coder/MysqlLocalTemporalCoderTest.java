package com.yulinlin.jdbc.mysql.coder;

import com.mysql.cj.BindValue;
import com.mysql.cj.MysqlType;
import com.mysql.cj.conf.DefaultPropertySet;
import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.protocol.ServerCapabilities;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.protocol.a.LocalDateValueEncoder;
import com.mysql.cj.protocol.a.LocalTimeValueEncoder;
import com.mysql.cj.protocol.a.NativePacketPayload;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

import static com.mysql.cj.protocol.a.NativeConstants.IntegerDataType.*;
import static org.junit.jupiter.api.Assertions.*;

class MysqlLocalTemporalCoderTest {
    @ParameterizedTest
    @ValueSource(strings = {"00:00:00", "13:14:15", "13:14:15.123456", "23:59:59.999999", "13:14:15.123456789"})
    void timeFieldsAndConditionsRetainFractionsAndDecode(String text) throws Exception {
        var value = LocalTime.parse(text);
        assertNativeFieldAndCondition(value);
        var coder = new MysqlTemporalCoders.LocalTimeCoder();
        assertEquals(value, coder.decode(null, LocalTime.class, value));
        assertEquals(value, coder.decode(null, Entity.class.getDeclaredField("alarm"), text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2000-02-29", "1970-01-01", "2026-12-31"})
    void dateFieldsAndConditionsKeepCalendarDate(String text) throws Exception {
        var value = LocalDate.parse(text);
        assertNativeFieldAndCondition(value);
        var coder = new MysqlTemporalCoders.LocalDateCoder();
        assertEquals(value, coder.decode(null, LocalDate.class, value));
        assertEquals(value, coder.decode(null, Entity.class.getDeclaredField("birthday"), text));
        assertEquals(value, coder.decode(null, LocalDate.class, java.sql.Date.valueOf(value)));
    }

    @Test
    void nullsAndLegacyTimeDecodingRemainSupported() {
        var time = new MysqlTemporalCoders.LocalTimeCoder();
        assertEquals(LocalTime.of(13, 14, 15), time.decode(null, LocalTime.class, Time.valueOf("13:14:15")));
        assertNull(time.encode(null, null, null));
        assertNull(time.decode(null, LocalTime.class, null));
        var date = new MysqlTemporalCoders.LocalDateCoder();
        assertNull(date.encode(null, null, null));
        assertNull(date.decode(null, LocalDate.class, null));
    }

    // Real Connector/J text and binary encoders; no external MySQL server is required.
    @ParameterizedTest
    @CsvSource({"GMT,00:00:00,00:00:00", "GMT,13:14:15.123456,13:14:15.123456",
            "Asia/Tokyo,13:14:15.123456,13:14:15.123456", "America/New_York,23:59:59.999999,23:59:59.999999",
            "GMT,13:14:15.123456789,13:14:15.123457"})
    void driverTimeBindingKeepsMicrosAndOwnsRounding(String zone, String input, String expectedText) {
        var value = new MysqlTemporalCoders.LocalTimeCoder().encode(null, null, LocalTime.parse(input));
        var expected = LocalTime.parse(expectedText);
        var binding = binding(value, MysqlType.TIME);
        var encoder = new LocalTimeValueEncoder();
        encoder.init(new DefaultPropertySet(), server(zone), null);
        assertEquals("'" + expectedText + "'", encoder.getString(binding));
        var packet = new NativePacketPayload(32);
        encoder.encodeAsBinary(packet, binding);
        packet.setPosition(0);
        assertEquals(expected.getNano() == 0 ? 8 : 12, packet.readInteger(INT1));
        assertEquals(0, packet.readInteger(INT1)); // Positive time, no elapsed days.
        assertEquals(0, packet.readInteger(INT4));
        assertEquals(expected.getHour(), packet.readInteger(INT1));
        assertEquals(expected.getMinute(), packet.readInteger(INT1));
        assertEquals(expected.getSecond(), packet.readInteger(INT1));
        if (expected.getNano() != 0) assertEquals(expected.getNano() / 1_000, packet.readInteger(INT4));
    }

    @Test
    void explicitDriverFractionalSecondsSettingRemainsEffective() {
        var properties = new DefaultPropertySet();
        properties.getBooleanProperty(PropertyKey.sendFractionalSeconds).setValue(false);
        var encoder = new LocalTimeValueEncoder();
        encoder.init(properties, server("GMT"), null);
        assertEquals("'13:14:15'", encoder.getString(binding(LocalTime.parse("13:14:15.123456"), MysqlType.TIME)));
    }

    @Test
    void driverDateBindingKeepsLeapDayInTextAndBinary() {
        var value = new MysqlTemporalCoders.LocalDateCoder().encode(null, null, LocalDate.of(2000, 2, 29));
        var binding = binding(value, MysqlType.DATE);
        var encoder = new LocalDateValueEncoder();
        encoder.init(new DefaultPropertySet(), server("GMT"), null);
        assertEquals("'2000-02-29'", encoder.getString(binding));
        var packet = new NativePacketPayload(16);
        encoder.encodeAsBinary(packet, binding);
        packet.setPosition(0);
        assertEquals(4, packet.readInteger(INT1));
        assertEquals(2000, packet.readInteger(INT2));
        assertEquals(2, packet.readInteger(INT1));
        assertEquals(29, packet.readInteger(INT1));
    }

    private static void assertNativeFieldAndCondition(Object value) {
        var buffer = new MysqlCoderManager().createEncoderBuffer();
        buffer.put("value", value);
        assertEquals(value, buffer.toMap().get("value"));
        var context = new SimpParamsContext(RequestType.select, Map.of(), buffer, Object.class, true);
        String key = context.putGetKey(value);
        assertEquals(value, buffer.toMap().get(key.substring(2, key.length() - 1)));
    }

    private static ServerSession server(String zone) {
        var capabilities = stub(ServerCapabilities.class, method -> switch (method) {
            case "serverSupportsFracSecs" -> true;
            default -> throw new AssertionError(method);
        });
        return stub(ServerSession.class, method -> switch (method) {
            case "getCapabilities" -> capabilities;
            case "getDefaultTimeZone" -> TimeZone.getTimeZone("Asia/Shanghai");
            case "getSessionTimeZone" -> TimeZone.getTimeZone(zone);
            case "isServerTruncatesFracSecs" -> false;
            default -> throw new AssertionError(method);
        });
    }

    private static BindValue binding(Object value, MysqlType type) {
        return stub(BindValue.class, method -> switch (method) {
            case "getValue" -> value;
            case "getMysqlType" -> type;
            case "getCalendar", "getField" -> null;
            default -> throw new AssertionError(method);
        });
    }

    private static <T> T stub(Class<T> type, Function<String, Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> calls.apply(method.getName())));
    }

    private static class Entity { private LocalDate birthday; private LocalTime alarm; }
}

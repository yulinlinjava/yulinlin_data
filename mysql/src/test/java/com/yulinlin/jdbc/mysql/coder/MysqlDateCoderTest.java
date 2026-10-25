package com.yulinlin.jdbc.mysql.coder;

import com.mysql.cj.BindValue;
import com.mysql.cj.MysqlType;
import com.mysql.cj.conf.DefaultPropertySet;
import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.protocol.ServerCapabilities;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.protocol.a.LocalDateTimeValueEncoder;
import com.mysql.cj.protocol.a.NativePacketPayload;
import com.mysql.cj.protocol.a.SqlTimestampValueEncoder;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.lang.util.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

import static com.mysql.cj.protocol.a.NativeConstants.IntegerDataType.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("java.util.TimeZone.default")
class MysqlDateCoderTest {
    private final TimeZone originalZone = TimeZone.getDefault();
    private final MysqlTemporalCoders.DateCoder coder = new MysqlTemporalCoders.DateCoder();
    private static final LocalDateTime LOCAL = LocalDateTime.of(2026, 10, 23, 21, 12, 38, 123_000_000);

    @AfterEach
    void restoreTimeZone() { TimeZone.setDefault(originalZone); }

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Shanghai", "UTC", "America/New_York"})
    void dateAndDateTimeUseTheSameLocalNumbersAndRoundTrip(String zone) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        for (LocalDateTime local : new LocalDateTime[]{LOCAL, LocalDateTime.of(2024, 2, 29, 0, 0)}) {
            Date date = Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
            Object encoded = coder.encode(null, null, date);
            assertInstanceOf(LocalDateTime.class, encoded);
            assertEquals(local, encoded);
            assertEquals(new MysqlTemporalCoders.DateTimeCoder().encode(null, null, DateTime.date(date)), encoded);
            assertEquals(date, coder.decode(null, Date.class, encoded));
            assertEquals(date, coder.decode(null, Entity.class.getDeclaredField("payTime"),
                    Timestamp.valueOf(local).toString()));
        }
    }

    @Test
    void entityFieldsAndQueryParametersUseTheRegisteredCoder() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        MysqlCoderManager manager = new MysqlCoderManager();
        Date date = DateTime.date(LOCAL).toDate();
        var buffer = manager.createEncoderBuffer();
        buffer.put("payTime", date);
        buffer.put("crtTime", DateTime.date(date));
        assertEquals(LOCAL, buffer.toMap().get("payTime"));
        assertEquals(buffer.toMap().get("payTime"), buffer.toMap().get("crtTime"));
        var context = new SimpParamsContext(RequestType.select, Map.of(), buffer, Object.class, true);
        String key = context.putGetKey(date);
        assertEquals(LOCAL, buffer.toMap().get(key.substring(2, key.length() - 1)));
        String dateTimeKey = context.putGetKey(DateTime.date(date));
        assertEquals(LOCAL, buffer.toMap().get(dateTimeKey.substring(2, dateTimeKey.length() - 1)));
        assertNull(coder.encode(null, null, null));
        assertNull(coder.decode(null, Date.class, null));
        assertNull(new MysqlTemporalCoders.DateTimeCoder().encode(null, null, null));
    }

    // Real Connector/J encoders, no database/network. Exercise text and server-prepared binary formats.
    @ParameterizedTest
    @CsvSource({"GMT,true", "GMT,false", "UTC,true", "UTC,false", "Asia/Tokyo,true", "Asia/Tokyo,false"})
    void mysqlDriverKeepsNumbersDespiteDifferentConnectionTimeZone(String connectionZone, boolean preserveInstants) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        var properties = new DefaultPropertySet();
        properties.getBooleanProperty(PropertyKey.preserveInstants).setValue(preserveInstants);
        var capabilities = stub(ServerCapabilities.class, method -> switch (method) {
            case "serverSupportsFracSecs" -> true;
            default -> throw new AssertionError(method);
        });
        var server = stub(ServerSession.class, method -> switch (method) {
            case "getCapabilities" -> capabilities;
            case "getDefaultTimeZone" -> TimeZone.getDefault();
            case "getSessionTimeZone" -> TimeZone.getTimeZone(connectionZone);
            case "isServerTruncatesFracSecs" -> false;
            default -> throw new AssertionError(method);
        });
        Date date = DateTime.date(LOCAL).toDate();
        LocalDateTime encoded = assertInstanceOf(LocalDateTime.class, coder.encode(null, null, date));
        BindValue binding = binding(encoded, MysqlType.DATETIME);
        var encoder = new LocalDateTimeValueEncoder();
        encoder.init(properties, server, null);
        assertEquals("'2026-10-23 21:12:38.123'", encoder.getString(binding));

        var packet = new NativePacketPayload(32);
        encoder.encodeAsBinary(packet, binding);
        packet.setPosition(0);
        assertEquals(11, packet.readInteger(INT1));
        assertEquals(2026, packet.readInteger(INT2));
        assertEquals(10, packet.readInteger(INT1));
        assertEquals(23, packet.readInteger(INT1));
        assertEquals(21, packet.readInteger(INT1));
        assertEquals(12, packet.readInteger(INT1));
        assertEquals(38, packet.readInteger(INT1));
        assertEquals(123_000, packet.readInteger(INT4));

        if (preserveInstants && connectionZone.equals("UTC")) {
            var legacy = new SqlTimestampValueEncoder();
            legacy.init(properties, server, null);
            assertEquals("'2026-10-23 13:12:38.123'",
                    legacy.getString(binding(new Timestamp(date.getTime()), MysqlType.TIMESTAMP)));
        }
    }

    private static BindValue binding(Object value, MysqlType type) {
        return stub(BindValue.class, method -> switch (method) {
            case "getValue" -> value;
            case "getMysqlType" -> type;
            case "getCalendar", "getField" -> null;
            case "keepOrigNanos" -> false;
            default -> throw new AssertionError(method);
        });
    }

    private static <T> T stub(Class<T> type, Function<String, Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> calls.apply(method.getName())));
    }

    private static final class Entity { private Date payTime; }
}

package com.yulinlin.jdbc.h2.coder;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.jdbc.h2.H2ParseManager;
import com.yulinlin.jdbc.h2.H2Session;
import com.yulinlin.jdbc.session.SqlNode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class H2LocalTemporalCoderTest {
    @ParameterizedTest
    @ValueSource(strings = {"00:00:00", "13:14:15", "13:14:15.123456", "23:59:59.999999", "13:14:15.123456789"})
    void timeFieldsAndConditionsRetainFractionsAndDecode(String text) throws Exception {
        var value = LocalTime.parse(text);
        assertNativeFieldAndCondition(value);
        var coder = new H2TemporalCoders.LocalTimeCoder();
        assertEquals(value, coder.decode(null, LocalTime.class, value));
        assertEquals(value, coder.decode(null, Entity.class.getDeclaredField("alarm"), text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2000-02-29", "1970-01-01", "2026-12-31"})
    void dateFieldsAndConditionsKeepCalendarDate(String text) throws Exception {
        var value = LocalDate.parse(text);
        assertNativeFieldAndCondition(value);
        var coder = new H2TemporalCoders.LocalDateCoder();
        assertEquals(value, coder.decode(null, LocalDate.class, value));
        assertEquals(value, coder.decode(null, Entity.class.getDeclaredField("birthday"), text));
        assertEquals(value, coder.decode(null, LocalDate.class, java.sql.Date.valueOf(value)));
    }

    @Test
    void nullsAndLegacyTimeDecodingRemainSupported() {
        var time = new H2TemporalCoders.LocalTimeCoder();
        assertEquals(LocalTime.of(13, 14, 15), time.decode(null, LocalTime.class, Time.valueOf("13:14:15")));
        assertNull(time.encode(null, null, null));
        assertNull(time.decode(null, LocalTime.class, null));
        var date = new H2TemporalCoders.LocalDateCoder();
        assertNull(date.encode(null, null, null));
        assertNull(date.decode(null, LocalDate.class, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL"})
    void jdbcInsertUpdateAndConditionsPreserveTimePrecisionAndDate(String mode) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:local_temporal_" + UUID.randomUUID() + mode);
        var session = new H2Session(source);
        session.setCoderManager(new H2CoderManager());
        try (var connection = source.getConnection(); var ddl = connection.createStatement()) {
            ddl.execute("CREATE TABLE \"temporal_values\" (\"id\" INT PRIMARY KEY, \"day\" DATE,"
                    + " \"clock\" TIME(9), \"micro_clock\" TIME(6))");
            var date = LocalDate.of(2000, 2, 29);
            int id = 0;
            for (String text : List.of("00:00:00", "13:14:15", "13:14:15.123456", "23:59:59.999999", "13:14:15.123456789")) {
                var time = LocalTime.parse(text);
                var insert = new InsertWrapper<>().table("temporal_values");
                insert.fields().field("id", ++id).field("day", date).field("clock", time).field("micro_clock", time);
                var insertSql = sql(insert, RequestType.insert);
                assertEquals(List.of(id, date, time, time), insertSql.getList());
                execute(connection, session, insertSql);
                var select = new SelectWrapper<>().table("temporal_values");
                select.fields().field("day").field("clock").field("micro_clock");
                select.where().eq("day", date).eq("clock", time);
                var selectSql = sql(select, RequestType.select);
                assertEquals(List.of(date, time), selectSql.getList());
                ddl.execute("SET TIME ZONE '-05:00'");
                var rows = session.executeSelectNode(connection, selectSql);
                assertEquals(1, rows.size(), text);
                var row = rows.getFirst();
                assertEquals(date, row.getObject("day", LocalDate.class));
                assertEquals(time, row.getObject("clock", LocalTime.class));
                var micros = time.getNano() == 123_456_789 ? time.withNano(123_457_000) : time;
                assertEquals(micros, row.getObject("micro_clock", LocalTime.class));
            }
            var updatedDate = LocalDate.of(2026, 12, 31);
            var updatedTime = LocalTime.parse("18:19:20.654321987");
            var update = new UpdateWrapper<>().table("temporal_values");
            update.fields().field("day", updatedDate).field("clock", updatedTime);
            update.where().eq("id", 1);
            execute(connection, session, sql(update, RequestType.update));
            var updated = new SelectWrapper<>().table("temporal_values");
            updated.fields().field("clock");
            updated.where().eq("day", updatedDate).eq("clock", updatedTime);
            assertEquals(1, session.executeSelectNode(connection, sql(updated, RequestType.select)).size());

            ddl.execute("INSERT INTO \"temporal_values\" (\"id\") VALUES (99)");
            var nullRow = new SelectWrapper<>().table("temporal_values");
            nullRow.fields().field("day").field("clock");
            nullRow.where().eq("id", 99);
            var result = session.executeSelectNode(connection, sql(nullRow, RequestType.select)).getFirst();
            assertNull(result.getObject("day", LocalDate.class));
            assertNull(result.getObject("clock", LocalTime.class));
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    private static void execute(Connection connection, H2Session session, SqlNode sql) throws Exception {
        try (var statement = connection.prepareStatement(sql.getSql())) {
            for (int i = 0; i < sql.getList().size(); i++) session.bindMaintenanceValue(statement, i + 1, sql.getList().get(i));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static SqlNode sql(INode node, RequestType type) {
        var context = new SimpParamsContext(type, Map.of(), new H2CoderManager().createEncoderBuffer(), Map.class, true);
        return (SqlNode) ((ParseResult) new H2ParseManager().parse(node, context)).getRequest();
    }

    private static void assertNativeFieldAndCondition(Object value) {
        var buffer = new H2CoderManager().createEncoderBuffer();
        buffer.put("value", value);
        assertEquals(value, buffer.toMap().get("value"));
        var context = new SimpParamsContext(RequestType.select, Map.of(), buffer, Object.class, true);
        String key = context.putGetKey(value);
        assertEquals(value, buffer.toMap().get(key.substring(2, key.length() - 1)));
    }

    private static class Entity { private LocalDate birthday; private LocalTime alarm; }
}

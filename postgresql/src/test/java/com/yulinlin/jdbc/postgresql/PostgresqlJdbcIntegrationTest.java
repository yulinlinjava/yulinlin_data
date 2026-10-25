package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.DeleteWrapper;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.ds.PGSimpleDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static com.yulinlin.jdbc.postgresql.PostgresqlTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "pg.test.url", matches = "jdbc:postgresql:.*")
class PostgresqlJdbcIntegrationTest {
    @ParameterizedTest
    @CsvSource({"Asia/Shanghai,UTC", "Asia/Shanghai,Asia/Shanghai", "Asia/Shanghai,America/New_York",
            "UTC,UTC", "UTC,Asia/Shanghai", "UTC,America/New_York",
            "America/New_York,UTC", "America/New_York,Asia/Shanghai", "America/New_York,America/New_York"})
    @ResourceLock("java.util.TimeZone.default")
    void timestampWithoutTimeZonePreservesDateAndDateTimeDigits(String jvmZone, String databaseZone) throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
        var session = new PostgresqlSession(new PGSimpleDataSource());
        session.setCoderManager(new PostgresqlCoderManager());
        try (var connection = connect()) {
            var local = LocalDateTime.of(2026, 10, 23, 21, 12, 38, 123_000_000);
            Date date = DateTime.date(local).toDate();
            var birthday = LocalDate.of(2000, 2, 29);
            var alarm = LocalTime.of(13, 14, 15, 123_456_000);
            try (var statement = connection.createStatement()) {
                statement.execute("SET TIME ZONE '" + databaseZone + "'");
                statement.execute("CREATE TEMP TABLE temporal_samples (id bigint, pay_time timestamp(6),"
                        + " event_time timestamp(6), birthday date, alarm time(6), enabled boolean)");
            }
            var insert = new InsertWrapper<>().table("pg_temp.temporal_samples");
            insert.fields().field("id", 1L).field("pay_time", date).field("eventTime", DateTime.date(local))
                    .field("birthday", birthday).field("alarm", alarm).field("enabled", false);
            try (var statement = prepare(connection, sql(insert, RequestType.insert))) {
                assertEquals(1, statement.executeUpdate());
            }
            var select = new SelectWrapper<>().table("pg_temp.temporal_samples");
            select.fields().field("pay_time", "payTime").field("eventTime").field("birthday")
                    .field("alarm").field("enabled");
            select.where().eq("pay_time", date).eq("eventTime", DateTime.date(local))
                    .eq("birthday", birthday).eq("alarm", alarm);
            var selectSql = sql(select, RequestType.select);
            assertEquals(List.of(local, local, birthday, alarm), selectSql.getList());
            // Changing the server session zone must not affect timestamp-without-time-zone values.
            try (var statement = connection.createStatement()) {
                statement.execute("SET TIME ZONE '" + (databaseZone.equals("UTC") ? "Asia/Tokyo" : "UTC") + "'");
            }
            var rows = session.executeSelectNode(connection, selectSql);
            assertEquals(1, rows.size());
            var row = rows.getFirst();
            assertEquals(local, row.getObject("eventTime", LocalDateTime.class));
            assertEquals(local, row.getObject("payTime", LocalDateTime.class));
            assertEquals(date, row.getObject("payTime", Date.class));
            DateTime decoded = row.getObject("eventTime", DateTime.class);
            assertEquals(date, decoded.toDate());
            assertEquals(birthday, row.getObject("birthday", LocalDate.class));
            assertEquals(alarm, row.getObject("alarm", LocalTime.class));
            assertEquals(false, row.getObject("enabled", Boolean.class));
            try (var statement = connection.createStatement()) {
                statement.execute("UPDATE pg_temp.temporal_samples SET enabled = NULL");
            }
            assertNull(session.executeSelectNode(connection, sql(select, RequestType.select))
                    .getFirst().getObject("enabled", Boolean.class));
            var precise = local.withNano(123_456_000);
            var update = new UpdateWrapper<>().table("pg_temp.temporal_samples");
            update.fields().field("eventTime", DateTime.date(precise));
            update.where().eq("pay_time", date);
            try (var statement = prepare(connection, sql(update, RequestType.update))) {
                assertEquals(1, statement.executeUpdate());
            }
            var preciseSelect = new SelectWrapper<>().table("pg_temp.temporal_samples");
            preciseSelect.fields().field("eventTime");
            preciseSelect.where().eq("eventTime", DateTime.date(precise));
            var preciseRows = session.executeSelectNode(connection, sql(preciseSelect, RequestType.select));
            assertEquals(1, preciseRows.size());
            DateTime preciseDecoded = preciseRows.getFirst().getObject("eventTime", DateTime.class);
            assertEquals(precise, preciseDecoded.toLocalDateTime());
        } finally {
            session.getThreadPoolExecutor().shutdownNow();
            TimeZone.setDefault(original);
        }
    }

    @Test
    void crudWholeJsonReplacementNumericIncrementAndRollback() throws Exception {
        try (var connection = connect(); var ddl = connection.createStatement()) {
            ddl.execute("CREATE TEMP TABLE pg_samples (id bigint PRIMARY KEY, document_data jsonb, amount numeric)");
            var insert = new InsertWrapper<>().table("pg_temp.pg_samples");
            insert.fields().field("id", 1L).field("payloadData", Map.of("name", "before"))
                    .field("amount", new BigDecimal("1.25"));
            try (var statement = prepare(connection, sql(insert, RequestType.insert))) {
                assertEquals(1, statement.executeUpdate());
            }
            connection.setAutoCommit(false);
            var update = new UpdateWrapper<>().table("pg_temp.pg_samples");
            update.fields().field("payloadData", Map.of("name", "after")).inc("amount", new BigDecimal("0.15"));
            update.where().eq("id", 1L);
            try (var statement = prepare(connection, sql(update, RequestType.update))) {
                assertEquals(1, statement.executeUpdate());
            }
            var read = new SelectWrapper<>().table("pg_temp.pg_samples");
            read.fields().field("amount");
            read.where().expression("document_data->>'name' = #{name}", Map.of("name", "after"));
            assertEquals(List.of("1.4"), query(connection, sql(read, RequestType.select)));
            connection.rollback();
            var afterRollback = new SelectWrapper<>().table("pg_temp.pg_samples");
            afterRollback.fields().field("amount");
            afterRollback.where().expression("document_data->>'name' = #{name}", Map.of("name", "before"));
            assertEquals(List.of("1.25"), query(connection, sql(afterRollback, RequestType.select)));
            var delete = new DeleteWrapper<>().table("pg_temp.pg_samples");
            delete.where().expression("document_data->>'name' = #{name}", Map.of("name", "before"));
            try (var statement = prepare(connection, sql(delete, RequestType.delete))) {
                assertEquals(1, statement.executeUpdate());
            }
            connection.commit();
        }
    }
}

package com.yulinlin.admin.risk;

import com.yulinlin.security.risk.RiskReplay;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalRiskHistoryTest {
    @Test
    void localYamlLoadsAndOrmReadsBoundedHistoryWithoutChangingRows() throws Exception {
        var config = LocalRiskHistory.load(new ClassPathResource("application-risk-local.example.yml"));
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:risk_history_" + UUID.randomUUID());
        config.datasource().setUrl(source.getURL());
        var h = config.history();
        h.setUserId("user'one"); // The value must be bound, never concatenated into SQL.
        h.setStart("2026-11-05 00:00:00.123");
        h.setEnd("2026-11-05 00:01:00.123");
        h.setIdColumn("id");
        var start = LocalDateTime.of(2026, 11, 5, 0, 0, 0, 123_000_000);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE \"video_history\" (\"id\" INT PRIMARY KEY, \"user_id\" VARCHAR(40),"
                    + " \"video_id\" VARCHAR(40), \"crt_time\" TIMESTAMP(3))");
            try (var insert = connection.prepareStatement("INSERT INTO \"video_history\" VALUES (?, ?, ?, ?)")) {
                // Insert in reverse order so the ORM order-by is exercised.
                for (int i = 53; i >= 0; i--) {
                    insert.setInt(1, i);
                    insert.setString(2, i == 53 ? "other-user" : h.getUserId());
                    insert.setString(3, "video-" + i);
                    insert.setObject(4, i == 51 ? start.minusSeconds(1) : i == 52 ? start.plusMinutes(1) : start.plusSeconds(i));
                    insert.executeUpdate();
                }
            }
            var events = LocalRiskHistory.read(config);
            assertEquals(51, events.size());
            assertEquals("0", events.getFirst().eventId());
            assertEquals(start.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(), events.getFirst().occurredAt());
            var results = new RiskReplay(config.risk()).replay(events);
            assertTrue(results.subList(0, 50).stream().allMatch(row -> row.decision().matches().isEmpty()));
            assertEquals("rapid", results.getLast().decision().matches().getFirst().ruleId());

            h.setMaxRecords(50);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> LocalRiskHistory.read(config))
                    .getMessage().contains("本次未回放"));
            h.setMaxRecords(51);
            h.setIdColumn("");
            assertTrue(LocalRiskHistory.read(config).stream().allMatch(event -> event.eventId() == null));
            try (var count = statement.executeQuery("SELECT COUNT(*) FROM \"video_history\"")) {
                assertTrue(count.next());
                assertEquals(54, count.getInt(1));
            }
        }
    }

    @Test
    void incompleteScopeAndSqlExpressionsFailBeforeAnyConnection() throws Exception {
        var config = LocalRiskHistory.load(new ClassPathResource("application-risk-local.example.yml"));
        config.datasource().setUrl("jdbc:mysql://invalid.invalid/test");
        config.history().setStart("2026-11-05 00:00:00");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LocalRiskHistory.read(config))
                .getMessage().contains("同时填写"));
        config.history().setEnd("2026-11-06 00:00:00");
        config.history().setTable("video_history; DELETE FROM video_history");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LocalRiskHistory.read(config))
                .getMessage().contains("普通标识符"));
    }

    @Test
    void todayIncludesAllUsersAcrossPagesAndExcludesMidnightAndRunTimeBoundaries() throws Exception {
        var config = LocalRiskHistory.load(new ClassPathResource("application-risk-local.example.yml"));
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:risk_today_" + UUID.randomUUID());
        config.datasource().setUrl(source.getURL());
        config.history().setPageSize(3);
        var clock = Clock.fixed(Instant.parse("2026-11-08T04:00:00Z"), ZoneOffset.UTC);
        var start = LocalDateTime.of(2026, 11, 8, 0, 0);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE \"video_history\" (\"id\" INT PRIMARY KEY, \"user_id\" VARCHAR(40),"
                    + " \"video_id\" VARCHAR(40), \"crt_time\" TIMESTAMP(3))");
            try (var insert = connection.prepareStatement("INSERT INTO \"video_history\" VALUES (?, ?, ?, ?)")) {
                for (int i = 12; i >= 0; i--) {
                    insert.setInt(1, i);
                    insert.setString(2, i % 2 == 0 ? "user-a" : "user-b");
                    insert.setString(3, "v" + i);
                    insert.setObject(4, i == 10 ? start.minusNanos(1_000_000)
                            : i == 11 ? start.plusHours(12) : i == 12 ? start.plusDays(1) : start.plusSeconds(i / 2));
                    insert.executeUpdate();
                }
            }
            var loaded = LocalRiskHistory.read(config, clock);
            assertEquals(start, loaded.range().start());
            assertEquals(start.plusHours(12), loaded.range().end());
            assertEquals(10, loaded.events().size());
            assertEquals(2, loaded.events().stream().map(e -> e.userId()).distinct().count());
            assertEquals(10, loaded.events().stream().map(e -> e.eventId()).distinct().count());
            assertEquals("0", loaded.events().getFirst().eventId());
            config.history().setIdColumn("");
            assertTrue(assertThrows(IllegalArgumentException.class, () -> LocalRiskHistory.read(config, clock))
                    .getMessage().contains("唯一的 id-column"));
        }
    }
}

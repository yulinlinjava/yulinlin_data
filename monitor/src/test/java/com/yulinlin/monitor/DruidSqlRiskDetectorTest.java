package com.yulinlin.monitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class DruidSqlRiskDetectorTest {
    private final SqlRiskDetector detector = new DruidSqlRiskDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1;",
            "SELECT 1; \r\n\t",
            "SELECT 1; /* trailing ; SELECT 2 */",
            "SELECT 1; -- trailing ; SELECT 2\n",
            "SELECT 1; # trailing ; SELECT 2\n",
            "SELECT '; SELECT 2' AS text",
            "SELECT 'it''s; SELECT 2' AS text",
            "SELECT 'it\\'s; SELECT 2' AS text",
            "SELECT \"; SELECT 2\" AS text",
            "SELECT `semi;colon` FROM user_info",
            "SELECT /* ; SELECT 2 */ 1",
            "SELECT 1 -- ; SELECT 2\n AS id",
            "SELECT 1 # ; SELECT 2\n AS id",
            "SELECT '; -- /*' AS text; /* ; SELECT 2 */"
    })
    void allowsSemicolonsThatDoNotStartAnotherStatement(String sql) {
        // Repeat against the same provider to cover its built-in cache as well.
        for (int i = 0; i < 2; i++) {
            var result = detector.check(sql);
            assertTrue(result.allowed(), () -> sql + ": " + result.reasons());
            assertTrue(result.reasons().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1; SELECT 2",
            "SELECT 1;\r\n\tSELECT 2;",
            "SELECT 1; /* ordinary comment */ SELECT 2",
            "SELECT 1; -- ordinary comment\n SELECT 2",
            "SELECT 1; # ordinary comment\n SELECT 2",
            "SELECT '; SELECT 2' AS text; SELECT 3",
            "SELECT 'it''s; SELECT 2' AS text; SELECT 3",
            "SELECT 1 /* ; SELECT 2 */; SELECT 3"
    })
    void rejectsActualSecondStatementsEvenWhenEachStatementIsHarmless(String sql) {
        for (int i = 0; i < 2; i++) {
            var result = detector.check(sql);
            assertFalse(result.allowed(), sql);
            assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("multi-statement")),
                    () -> sql + ": " + result.reasons());
        }
    }

    @Test void cachedSingleStatementDoesNotAllowAnAppendedStatement() {
        String single = "SELECT '; SELECT 2' AS text; /* trailing ; */";
        String stacked = single + " SELECT 3";
        for (int i = 0; i < 2; i++) {
            assertTrue(detector.check(single).allowed());
            assertFalse(detector.check(stacked).allowed());
            assertTrue(detector.check(single).allowed());
        }
    }

    @Test void rejectedStackedStatementDoesNotPoisonTheSingleStatementCache() {
        String single = "SELECT '; SELECT 2' AS text; /* trailing ; */";
        for (int i = 0; i < 2; i++) {
            assertFalse(detector.check(single + " SELECT 3").allowed());
            assertTrue(detector.check(single).allowed());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT `id`, `name` FROM `user_info` WHERE `id` = ? LIMIT 0, 15",
            "SELECT * FROM user_info WHERE name = ?;",
            "SELECT '; DROP TABLE example' AS text",
            "SELECT `semi;colon` FROM `user_info`",
            "SELECT * FROM user_info /* semicolon ; SELECT 2 */ WHERE id = ? -- trailing ;",
            "INSERT INTO user_info (id, name) VALUES (?, ?), (?, ?)",
            "UPDATE user_info SET gold = gold + ?, upt_time = ? WHERE id = ?",
            "DELETE FROM user_info WHERE id IN (?, ?)",
            "SELECT status, COUNT(*) AS total FROM user_info GROUP BY status HAVING total > ? ORDER BY total DESC",
            "SELECT COUNT(1) FROM (SELECT status FROM user_info GROUP BY status) temp",
            "SELECT id FROM user_info UNION ALL SELECT id FROM user_archive",
            "EXPLAIN SELECT id FROM user_info WHERE status = ?",
            "SELECT * FROM user_info WHERE id = ? FOR UPDATE",
            "CREATE TABLE IF NOT EXISTS example (id BIGINT PRIMARY KEY)",
            "ALTER TABLE example ADD COLUMN name VARCHAR(20)"
    })
    void allowsNormalTemplatesAndQuotedSemicolons(String sql) {
        var result = detector.check(sql);
        assertTrue(result.allowed(), () -> result.reasons().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM user_info; DELETE FROM user_info WHERE id = ?",
            "SELECT SLEEP(?)",
            "SELECT BENCHMARK(?, MD5('x'))",
            "SELECT LOAD_FILE(?)",
            "SELECT SLEEP(?) FROM information_schema.tables",
            "SELECT SLEEP(?) FROM user_info",
            "SELECT `sleep`(?)",
            "INSERT INTO user_info (id) VALUES (SLEEP(?))",
            "SELECT (SELECT SLEEP(?)) FROM user_info",
            "SELECT * FROM user_info INTO OUTFILE '/tmp/risk-test'",
            "DROP TABLE user_info",
            "TRUNCATE TABLE user_info",
            "SELECT * FROM user_info WHERE id = ? OR 1 = 1"
    })
    void rejectsRiskyTemplates(String sql) {
        for (int i = 0; i < 2; i++) {
            var result = detector.check(sql);
            assertFalse(result.allowed(), sql);
            assertFalse(result.reasons().isEmpty());
        }
    }

    @Test void unsupportedSyntaxIsAnInspectionFailureEvenOnRepeatedChecks() {
        for (int i = 0; i < 2; i++)
            assertThrows(IllegalArgumentException.class, () -> detector.check("SELECT * FROM"));
    }

    @Test void unsupportedExecutableCommentIsNotSilentlyAccepted() {
        assertThrows(IllegalArgumentException.class, () -> detector.check("SELECT /*!50000 SLEEP(1) */"));
    }

    @Test void providerCanBeReusedAcrossThreads() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 100).<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () ->
                    detector.check(i % 2 == 0 ? "SELECT id FROM users WHERE id = ?" : "SELECT SLEEP(?)")
                            .allowed() == (i % 2 == 0)).toList();
            for (var result : executor.invokeAll(tasks)) assertTrue(result.get());
        }
    }
}

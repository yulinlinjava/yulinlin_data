package com.yulinlin.jdbc.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("com.yulinlin.jdbc.log.JdbcExecutionLog.logger")
class JdbcPrettySqlLogTest {
    private static final String SQL = "select \"area\", 'C:\\data' from \"domain_probe_provider_config\" where \"id\" = ?";
    private static final String READABLE_SQL = "select area, 'C:\\data' from domain_probe_provider_config where id = ?";
    private final Logger logger = (Logger) LoggerFactory.getLogger(JdbcExecutionLog.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;
    private boolean previousAdditive;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        logger.setLevel(Level.ALL);
        logger.setAdditive(false);
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogs() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditive);
    }

    @Test
    void prettyPrintingIsEnabledByDefault() {
        var properties = new JdbcSessionProperties();
        assertTrue(properties.isLogSqlPretty());
        properties.setLog(true);
        var event = print(properties, List.of(request(SQL)), false, null, 1);
        assertTrue(event.getFormattedMessage().endsWith("\nSQL=" + READABLE_SQL));
        assertFalse(event.getFormattedMessage().contains("\n参数="));
    }

    @ParameterizedTest
    @CsvSource({
            "true,false,success", "false,false,success", "true,true,success", "false,true,success",
            "true,false,slow", "false,false,slow", "true,true,slow", "false,true,slow",
            "true,false,error", "false,false,error", "true,true,error", "false,true,error"
    })
    void appliesToSingleAndBatchSuccessSlowAndErrorLogs(boolean pretty, boolean batch, String outcome) {
        var properties = properties(pretty);
        // Slow and failed operations must still use the flag even with ordinary success logging disabled.
        properties.setLog(outcome.equals("success"));
        properties.setSlowSqlThreshold(Duration.ofMillis(10));
        var first = request(SQL);
        var second = request("select \"node_ids\" from \"domain_probe_provider_config\"");
        var requests = batch ? List.of(first, second, first) : List.of(first);
        var failure = outcome.equals("error") ? new IllegalStateException("test failure") : null;
        var event = print(properties, requests, batch, failure, outcome.equals("slow") ? 20 : 1);
        String firstRendered = pretty ? READABLE_SQL : quoted(SQL);
        String secondRendered = pretty ? "select node_ids from domain_probe_provider_config"
                : quoted(((SqlNode) second.getRequest()).getSql());
        String expected = batch ? "[" + firstRendered + ", " + secondRendered + "]" : firstRendered;
        assertTrue(event.getFormattedMessage().contains("\nSQL=" + expected + "\n参数=[]"));
        if (batch) {
            assertTrue(event.getFormattedMessage().contains("SQL模板数=2"));
            assertTrue(event.getFormattedMessage().contains("操作数量=3"));
        }
        assertEquals(switch (outcome) {
            case "slow" -> Level.WARN;
            case "error" -> Level.ERROR;
            default -> Level.INFO;
        }, event.getLevel());
        assertEquals(failure != null, event.getThrowableProxy() != null);
        assertEquals(SQL, ((SqlNode) first.getRequest()).getSql());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothModesKeepSingleLineSanitizingAndTruncation(boolean pretty) {
        var properties = properties(pretty);
        String raw = "select\r\n\t\"area\"\u0000 from \"config\" " + "x".repeat(5000);
        var request = request(raw);
        String normalized = (pretty ? "select area from config " : "select \"area\" from \"config\" ") + "x".repeat(5000);
        String limited = normalized.substring(0, 4093) + "...";
        String message = print(properties, List.of(request), false, null, 1).getFormattedMessage();
        assertTrue(message.contains("\nSQL=" + (pretty ? limited : quoted(limited)) + "\n参数=[]"));
        assertEquals(3, message.lines().count());
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\t"));
        assertFalse(message.contains("\u0000"));
        assertEquals(raw, ((SqlNode) request.getRequest()).getSql());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothModesKeepBatchTemplateLimits(boolean pretty) {
        var requests = IntStream.range(0, 7)
                .mapToObj(i -> request("select \"column" + i + "\" " + "x".repeat(2000))).toList();
        String message = print(properties(pretty), requests, true, null, 1).getFormattedMessage();
        assertTrue(message.contains("SQL模板数=7"));
        String first = (pretty ? "select column0 " + "x".repeat(2000)
                : ((SqlNode) requests.getFirst().getRequest()).getSql()).substring(0, 1021) + "...";
        assertTrue(message.contains("\nSQL=[" + (pretty ? first : quoted(first)) + ", "));
        assertTrue(message.contains((pretty ? "...(+2)" : quoted("...(+2)")) + "]\n参数=[]"));
        assertFalse(message.contains("column5"));
        assertFalse(message.contains("column6"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missingSqlAndEmptyBatchesStillRender(boolean pretty) {
        var properties = properties(pretty);
        String message = print(properties, List.of(request(null)), false, null, 1).getFormattedMessage();
        assertTrue(message.contains("\nSQL=" + (pretty ? "<unknown>" : quoted("<unknown>")) + "\n参数=[]"));
        appender.list.clear();
        message = print(properties, List.of(), true, null, 1).getFormattedMessage();
        assertTrue(message.contains("SQL模板数=0\nSQL=[]\n参数=[]"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bindsKebabCasePropertyOnAnInheritedConfiguration(boolean pretty) {
        var binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "yulinlin.test.log-sql-pretty", pretty)));
        var properties = binder.bind("yulinlin.test", Bindable.of(TestProperties.class)).get();
        assertEquals(pretty, properties.isLogSqlPretty());
    }

    private ILoggingEvent print(JdbcSessionProperties properties, List<ParseResult> requests,
                               boolean batch, Throwable failure, long elapsedMillis) {
        // Only the log observer is called; no connection or database is used.
        var session = new JdbcSession(new DriverManagerDataSource());
        // H2/PostgreSQL 风格；MySQL 引用规则在 SqlLogFormatterTest 中独立验证。
        session.setParseManager(new com.yulinlin.jdbc.sql.SqlParseManager() {
            @Override public com.yulinlin.jdbc.sql.parse.NameParse nameParse() {
                return new com.yulinlin.jdbc.sql.parse.NameParse() {
                    @Override public String selectAlias(String name) { return '"' + name + '"'; }
                };
            }
        });
        session.setProperties(properties);
        JdbcExecutionLog.print(new SessionExecution(session, requests, elapsedMillis * 1_000_000,
                failure == null ? requests.size() : null, failure, batch));
        assertEquals(1, appender.list.size());
        return appender.list.getFirst();
    }

    private static ParseResult request(String sql) {
        var result = new ParseResult(ParseType.select, new SqlNode(sql), null);
        result.setTransactionId("sql-log-test");
        return result;
    }

    private static JdbcSessionProperties properties(boolean pretty) {
        var properties = new JdbcSessionProperties();
        properties.setLog(true);
        properties.setLogParams(true);
        properties.setLogSqlPretty(pretty);
        return properties;
    }

    private static String quoted(String sql) {
        return '"' + sql.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    public static class TestProperties extends JdbcSessionProperties { }
}

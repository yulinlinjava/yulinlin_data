package com.yulinlin.jdbc.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.session.AbstractJdbcSession;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlNodeLogTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(SqlNodeLog.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;
    private boolean previousAdditive;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        logger.setLevel(Level.ALL);
        logger.setAdditive(false);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditive);
    }

    @Test
    void ordinarySqlRequiresLogFlagAndDoesNotExposeParamsByDefault() {
        JdbcSessionProperties properties = new JdbcSessionProperties();
        AbstractJdbcSession session = session(properties);
        SqlNodeLog printer = new SqlNodeLog();
        ParseResult result = result(ParseType.select, "select *\nfrom users where id = ?", List.of("secret"));

        printer.success(12, result, session);
        assertThat(events.list).isEmpty();

        properties.setLog(true);
        printer.success(12, result, session);
        assertThat(events.list).hasSize(1);
        assertThat(events.list.getFirst().getLevel()).isEqualTo(Level.INFO);
        assertThat(events.list.getFirst().getFormattedMessage())
                .isEqualTo("SQL执行成功 group=mysql cluster=master type=select elapsedMs=12 "
                        + "sql=\"select * from users where id = ?\"")
                .doesNotContain("secret", "\n", "\r", "params=");
    }

    @Test
    void slowSqlWarnsOnceEvenWhenOrdinaryLoggingIsDisabled() {
        JdbcSessionProperties properties = new JdbcSessionProperties();
        properties.setSlowSqlThreshold(Duration.ofSeconds(1));
        SqlNodeLog printer = new SqlNodeLog();
        ParseResult result = result(ParseType.count, "select count(*) from users", null);

        printer.success(999, result, session(properties));
        printer.success(1_000, result, session(properties));

        assertThat(events.list).hasSize(1);
        assertThat(events.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(events.list.getFirst().getFormattedMessage())
                .startsWith("SQL慢查询 group=mysql cluster=master type=count elapsedMs=1000");
    }

    @Test
    void optionalParamsAreBoundedNullSafeAndDoNotDumpBinaryOrStreams() {
        JdbcSessionProperties properties = new JdbcSessionProperties();
        properties.setLog(true);
        properties.setLogParams(true);
        properties.setMaxParamLength(64);
        List<Object> params = Arrays.asList(null, new byte[128], new ByteArrayInputStream(new byte[32]),
                "line1\nline2");

        new SqlNodeLog().success(3,
                result(ParseType.insert, "insert into files values (?, ?, ?, ?)", params), session(properties));

        assertThat(events.list).hasSize(1);
        assertThat(events.list.getFirst().getFormattedMessage())
                .contains("params=[null, <byte[] length=128>, <ByteArrayInputStream length=unknown>, line1 line2]")
                .doesNotContain("\n", "\r");
        assertThat(SqlNodeLog.formatParams(List.of("abcdefghijk"), 8)).isEqualTo("[abcde...]");
    }

    @Test
    void batchOperationsProduceOneSummaryWithBoundedTemplateDetails() {
        JdbcSessionProperties properties = new JdbcSessionProperties();
        properties.setLog(true);
        properties.setLogParams(true);
        SqlNodeLog printer = new SqlNodeLog();
        List<ParseResult> rows = List.of(
                result(ParseType.insert, "insert into users values (?)", List.of(1)),
                result(ParseType.insert, "insert into users values (?)", List.of(2)),
                result(ParseType.insert, "insert into audit values (?)", List.of(3)));

        printer.successBatch(18, rows, 3, session(properties));

        assertThat(events.list).hasSize(1);
        assertThat(events.list.getFirst().getFormattedMessage())
                .isEqualTo("SQL批量执行成功 group=mysql cluster=master type=insert elapsedMs=18 nodes=3 "
                        + "affected=3 templates=2 sqls=[\"insert into users values (?)\", "
                        + "\"insert into audit values (?)\"] params=[1]");
    }

    @Test
    void errorsAlwaysProduceOneEntryWithOriginalThrowable() {
        JdbcSessionProperties properties = new JdbcSessionProperties();
        RuntimeException failure = new RuntimeException("database unavailable");

        new SqlNodeLog().error(42, failure,
                result(ParseType.update, "update users set name = ?", List.of("hidden")), session(properties));

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .startsWith("SQL执行异常 group=mysql cluster=master type=update elapsedMs=42")
                .doesNotContain("hidden");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("database unavailable");
    }

    @Test
    void batchFailureProducesOneSummaryAndOneThrowable() {
        JdbcSessionProperties properties = new JdbcSessionProperties();
        RuntimeException failure = new RuntimeException("batch failed");
        List<ParseResult> rows = List.of(
                result(ParseType.delete, "delete from users where id = ?", List.of(1)),
                result(ParseType.delete, "delete from users where id = ?", List.of(2)));

        new SqlNodeLog().errorBatch(37, failure, rows, session(properties));

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .isEqualTo("SQL批量执行异常 group=mysql cluster=master type=delete elapsedMs=37 nodes=2 "
                        + "templates=1 sqls=[\"delete from users where id = ?\"]");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("batch failed");
    }

    private static AbstractJdbcSession session(JdbcSessionProperties properties) {
        return new TestSession(properties);
    }

    private static ParseResult result(ParseType type, String sql, List<Object> params) {
        SqlNode node = new SqlNode(sql) {
            @Override
            public List<Object> getList() {
                return params;
            }
        };
        return new ParseResult(type, node, null);
    }

    private static final class TestSession extends AbstractJdbcSession {
        private TestSession(JdbcSessionProperties properties) {
            super(null);
            setProperties(properties);
            setGroup("mysql");
            setCluster(JoinCluster.master);
            getThreadPoolExecutor().shutdownNow();
        }

        @Override protected Integer executeUpdateNode(Connection connection, List<ParseResult> list) {
            throw new UnsupportedOperationException();
        }

        @Override protected List<IDataBuffer> executeSelectNode(Connection connection, SqlNode node) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean ping() { return true; }
        @Override public void shutdown() { }
    }
}

package com.yulinlin.jdbc.log;

import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.transaction.TransactionLog;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.session.AbstractJdbcSession;
import com.yulinlin.jdbc.session.SqlNode;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.sql.Blob;
import java.sql.Clob;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/** Session 直接调用；一条日志事件内按摘要、SQL、参数分行展示，内容保持有界。 */
@Slf4j
public final class JdbcExecutionLog {
    private static final int MAX_LOGGED_PARAMS = 20;
    private static final int MAX_LOGGED_TEMPLATES = 5;
    private static final int MAX_SQL_LENGTH = 4096;
    private static final int MAX_BATCH_SQL_LENGTH = 1024;

    private JdbcExecutionLog() {
    }

    public static void print(SessionExecution execution) {
        if (execution.batch()) {
            if (execution.error() == null) {
                successBatch(execution);
            } else {
                errorBatch(execution);
            }
        } else {
            ParseResult result = firstResult(execution.requests());
            if (execution.error() == null) success(execution.elapsedMillis(), result, execution.session());
            else error(execution.elapsedMillis(), execution.error(), result, execution.session());
        }
    }

    private static void success(long elapsedMs, ParseResult result, EntitySession session) {
        try {
            JdbcSessionProperties properties = properties(session);
            boolean slow = isSlow(elapsedMs, properties);
            if (!slow && !isSqlLogEnabled(properties, session)) return;
            if (slow) {
                if (log.isWarnEnabled()) log.warn(singleMessage("SQL慢查询", elapsedMs, result, session, properties));
            } else if (log.isInfoEnabled()) {
                log.info(singleMessage("SQL执行成功", elapsedMs, result, session, properties));
            }
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    private static void error(long elapsedMs, Throwable error, ParseResult result, EntitySession session) {
        try {
            if (!log.isErrorEnabled()) return;
            log.error(singleMessage("SQL执行失败", elapsedMs, result, session, properties(session)), error);
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    private static void successBatch(SessionExecution execution) {
        try {
            JdbcSessionProperties properties = properties(execution.session());
            boolean slow = isSlow(execution.elapsedMillis(), properties);
            if (!slow && !isSqlLogEnabled(properties, execution.session())) return;
            if (slow) {
                if (log.isWarnEnabled()) {
                    log.warn(batchMessage("SQL批量执行缓慢", execution, properties));
                }
            } else if (log.isInfoEnabled()) {
                log.info(batchMessage("SQL批量执行成功", execution, properties));
            }
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    private static void errorBatch(SessionExecution execution) {
        try {
            if (!log.isErrorEnabled()) return;
            log.error(batchMessage("SQL批量执行失败", execution, properties(execution.session())), execution.error());
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    private static String singleMessage(String event, long elapsedMs, ParseResult result, EntitySession session,
                                 JdbcSessionProperties properties) {
        SqlNode node = sqlNode(result);
        StringBuilder message = base(event, elapsedMs, result, session)
                .append("\nSQL=").append(quoteSql(node == null ? null : node.getSql()));
        appendParams(message, node, properties);
        return message.toString();
    }

    private static String batchMessage(String event, SessionExecution execution,
                                       JdbcSessionProperties properties) {
        List<ParseResult> results = execution.requests();
        ParseResult sampleResult = firstResult(results);
        SqlNode sample = sqlNode(sampleResult);
        SqlTemplateSummary templates = sqlTemplates(results);
        StringBuilder message = base(event, execution.elapsedMillis(), sampleResult, execution.session());
        // 使用原始纳秒计算分摊值，避免先截断为毫秒造成小批次平均值失真。
        if (execution.error() == null && !results.isEmpty()) {
            message.append(" 平均每条耗时=")
                    .append(String.format(Locale.ROOT, "%.3f", execution.elapsedNanos() / 1_000_000.0 / results.size()))
                    .append("ms");
        }
        message.append(" 操作数量=").append(results.size());
        if (execution.error() == null && execution.affectedRows() != null && execution.affectedRows() >= 0) {
            message.append(" 影响行数=").append(execution.affectedRows());
        }
        message.append(" SQL模板数=").append(templates.count())
                .append("\nSQL=").append(templates.rendered());
        appendParams(message, sample, properties);
        return message.toString();
    }

    private static StringBuilder base(String event, long elapsedMs, ParseResult result, EntitySession session) {
        return new StringBuilder(event)
                .append(" 事务ID=").append(token(result != null && result.getTransactionId() != null
                        ? result.getTransactionId() : TransactionLog.current().id()))
                .append(" 数据源=").append(token(group(session)))
                .append(" 节点=").append(token(cluster(session)))
                .append(" 操作=").append(result == null || result.getType() == null ? "unknown" : result.getType())
                .append(" 总耗时=").append(Math.max(-1, elapsedMs)).append("ms");
    }

    private static void appendParams(StringBuilder message, SqlNode node, JdbcSessionProperties properties) {
        if (properties == null || !properties.isLogParams()) return;
        message.append("\n参数=").append(formatParams(node == null ? null : node.getList(),
                properties.getMaxParamLength()));
    }

    static String formatParams(List<?> params, int maxParamLength) {
        if (params == null || params.isEmpty()) return "[]";
        int limit = Math.min(params.size(), MAX_LOGGED_PARAMS);
        StringJoiner values = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < limit; i++) values.add(formatParam(params.get(i), maxParamLength));
        if (params.size() > limit) values.add("...(+" + (params.size() - limit) + ")");
        return values.toString();
    }

    private static String formatParam(Object value, int maxLength) {
        if (value == null) return "null";
        String rendered;
        try {
            if (value instanceof byte[] bytes) {
                rendered = "<byte[] length=" + bytes.length + ">";
            } else if (value instanceof ByteBuffer buffer) {
                rendered = "<" + value.getClass().getSimpleName() + " length=" + buffer.remaining() + ">";
            } else if (value instanceof InputStream || value instanceof Reader
                    || value instanceof Blob || value instanceof Clob) {
                rendered = "<" + value.getClass().getSimpleName() + " length=unknown>";
            } else if (value.getClass().isArray()) {
                rendered = "<" + value.getClass().getComponentType().getTypeName()
                        + "[] length=" + Array.getLength(value) + ">";
            } else {
                rendered = token(value);
            }
        } catch (RuntimeException formattingFailure) {
            rendered = "<" + value.getClass().getSimpleName() + " formatting-error>";
        }
        return truncate(rendered, maxLength);
    }

    private static String quoteSql(String sql) {
        return quoteSql(sql, MAX_SQL_LENGTH);
    }

    private static String quoteSql(String sql, int maxLength) {
        if (sql == null) return "\"<unknown>\"";
        return '"' + truncate(singleLine(sql), maxLength)
                .replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static SqlTemplateSummary sqlTemplates(List<ParseResult> results) {
        if (results == null || results.isEmpty()) return new SqlTemplateSummary(0, "[]");
        Set<String> unique = new java.util.LinkedHashSet<>();
        for (ParseResult result : results) {
            SqlNode node = sqlNode(result);
            if (node != null) unique.add(node.getSql());
        }
        StringJoiner sqls = new StringJoiner(", ", "[", "]");
        int index = 0;
        for (String sql : unique) {
            if (index++ == MAX_LOGGED_TEMPLATES) break;
            sqls.add(quoteSql(sql, MAX_BATCH_SQL_LENGTH));
        }
        if (unique.size() > MAX_LOGGED_TEMPLATES) {
            sqls.add("\"...(+" + (unique.size() - MAX_LOGGED_TEMPLATES) + ")\"");
        }
        return new SqlTemplateSummary(unique.size(), sqls.toString());
    }

    private record SqlTemplateSummary(int count, String rendered) { }

    private static ParseResult firstResult(List<ParseResult> results) {
        if (results == null) return null;
        for (ParseResult result : results) if (result != null) return result;
        return null;
    }

    private static SqlNode sqlNode(ParseResult result) {
        return result != null && result.getRequest() instanceof SqlNode node ? node : null;
    }

    private static boolean isSlow(long elapsedMs, JdbcSessionProperties properties) {
        if (properties == null) return false;
        Duration threshold = properties.getSlowSqlThreshold();
        return threshold != null && !threshold.isZero()
                && Duration.ofMillis(Math.max(0, elapsedMs)).compareTo(threshold) >= 0;
    }

    private static boolean isSqlLogEnabled(JdbcSessionProperties properties, EntitySession session) {
        // Preserve the historical fallback for direct/non-JDBC invocations where no JDBC settings exist.
        return properties == null ? !(session instanceof AbstractJdbcSession) : properties.isLog();
    }

    private static JdbcSessionProperties properties(EntitySession session) {
        return session instanceof AbstractJdbcSession jdbcSession ? jdbcSession.getProperties() : null;
    }

    private static String group(EntitySession session) {
        return session == null ? "jdbc" : session.group();
    }

    private static Object cluster(EntitySession session) {
        return session == null ? "unknown" : session.cluster();
    }

    private static String token(Object value) {
        return singleLine(String.valueOf(value));
    }

    private static String singleLine(String value) {
        if (value == null || value.isEmpty()) return String.valueOf(value);
        StringBuilder result = new StringBuilder(value.length());
        boolean whitespace = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                whitespace = result.length() > 0;
            } else {
                if (whitespace) result.append(' ');
                result.append(ch);
                whitespace = false;
            }
        }
        return result.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        if (maxLength <= 3) return value.substring(0, maxLength);
        return value.substring(0, maxLength - 3) + "...";
    }

    private static void reportLoggingFailure(RuntimeException failure) {
        try {
            if (log.isDebugEnabled()) log.debug("SQL日志渲染失败", failure);
        } catch (RuntimeException ignored) {
            // Logging must never change the database operation's outcome.
        }
    }
}

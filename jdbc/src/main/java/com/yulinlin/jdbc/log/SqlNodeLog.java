package com.yulinlin.jdbc.log;

import com.yulinlin.data.core.log.LogPrint;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
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
import java.util.Set;
import java.util.StringJoiner;

/** Emits one bounded, single-line entry for each physical JDBC operation. */
@Slf4j
public class SqlNodeLog implements LogPrint<SqlNode> {
    private static final int MAX_LOGGED_PARAMS = 20;
    private static final int MAX_LOGGED_TEMPLATES = 5;
    private static final int MAX_SQL_LENGTH = 4096;
    private static final int MAX_BATCH_SQL_LENGTH = 1024;

    @Override
    public void success(long elapsedMs, ParseResult result) {
        success(elapsedMs, result, currentSession());
    }

    @Override
    public void success(long elapsedMs, ParseResult result, EntitySession session) {
        try {
            JdbcSessionProperties properties = properties(session);
            boolean slow = isSlow(elapsedMs, properties);
            if (!slow && !isSqlLogEnabled(properties, session)) return;
            if (slow) {
                if (log.isWarnEnabled()) log.warn(singleMessage("SQL SLOW", elapsedMs, result, session, properties));
            } else if (log.isInfoEnabled()) {
                log.info(singleMessage("SQL OK", elapsedMs, result, session, properties));
            }
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    @Override
    public void error(Throwable error, ParseResult result) {
        error(error, result, currentSession());
    }

    @Override
    public void error(Throwable error, ParseResult result, EntitySession session) {
        error(-1, error, result, session);
    }

    @Override
    public void error(long elapsedMs, Throwable error, ParseResult result, EntitySession session) {
        try {
            if (!log.isErrorEnabled()) return;
            log.error(singleMessage("SQL ERROR", elapsedMs, result, session, properties(session)), error);
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    @Override
    public void successBatch(long elapsedMs, List<ParseResult> results, int affected, EntitySession session) {
        try {
            JdbcSessionProperties properties = properties(session);
            boolean slow = isSlow(elapsedMs, properties);
            if (!slow && !isSqlLogEnabled(properties, session)) return;
            if (slow) {
                if (log.isWarnEnabled()) {
                    log.warn(batchMessage("SQL BATCH SLOW", elapsedMs, results, affected, session, properties));
                }
            } else if (log.isInfoEnabled()) {
                log.info(batchMessage("SQL BATCH OK", elapsedMs, results, affected, session, properties));
            }
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    @Override
    public void errorBatch(Throwable error, List<ParseResult> results, EntitySession session) {
        errorBatch(-1, error, results, session);
    }

    @Override
    public void errorBatch(long elapsedMs, Throwable error, List<ParseResult> results, EntitySession session) {
        try {
            if (!log.isErrorEnabled()) return;
            log.error(batchMessage("SQL BATCH ERROR", elapsedMs, results, -1, session, properties(session)), error);
        } catch (RuntimeException loggingFailure) {
            reportLoggingFailure(loggingFailure);
        }
    }

    private String singleMessage(String event, long elapsedMs, ParseResult result, EntitySession session,
                                 JdbcSessionProperties properties) {
        SqlNode node = sqlNode(result);
        StringBuilder message = base(event, elapsedMs, result, session)
                .append(" sql=").append(quoteSql(node == null ? null : node.getSql()));
        appendParams(message, node, properties);
        return message.toString();
    }

    private String batchMessage(String event, long elapsedMs, List<ParseResult> results, int affected,
                                EntitySession session, JdbcSessionProperties properties) {
        ParseResult sampleResult = firstResult(results);
        SqlNode sample = sqlNode(sampleResult);
        SqlTemplateSummary templates = sqlTemplates(results);
        StringBuilder message = base(event, elapsedMs, sampleResult, session)
                .append(" nodes=").append(results == null ? 0 : results.size());
        if (affected >= 0) message.append(" affected=").append(affected);
        message.append(" templates=").append(templates.count())
                .append(" sqls=").append(templates.rendered());
        appendParams(message, sample, properties, " sampleParams=");
        return message.toString();
    }

    private StringBuilder base(String event, long elapsedMs, ParseResult result, EntitySession session) {
        return new StringBuilder(event)
                .append(" group=").append(token(group(session)))
                .append(" cluster=").append(token(cluster(session)))
                .append(" type=").append(result == null || result.getType() == null ? "unknown" : result.getType())
                .append(" elapsedMs=").append(Math.max(-1, elapsedMs));
    }

    private void appendParams(StringBuilder message, SqlNode node, JdbcSessionProperties properties) {
        appendParams(message, node, properties, " params=");
    }

    private void appendParams(StringBuilder message, SqlNode node, JdbcSessionProperties properties,
                              String label) {
        if (properties == null || !properties.isLogParams()) return;
        message.append(label).append(formatParams(node == null ? null : node.getList(),
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

    private EntitySession currentSession() {
        return SessionUtil.route() == null ? null : SessionUtil.route().session();
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
            if (log.isDebugEnabled()) log.debug("SQL log rendering failed", failure);
        } catch (RuntimeException ignored) {
            // Logging must never change the database operation's outcome.
        }
    }
}

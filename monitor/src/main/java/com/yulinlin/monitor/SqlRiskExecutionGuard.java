package com.yulinlin.monitor;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSqlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Monitor-owned optional adapter. JDBC itself has no dependency on Druid or monitor. */
public final class SqlRiskExecutionGuard implements JdbcSqlGuard {
    private static final Logger LOG = LoggerFactory.getLogger(SqlRiskExecutionGuard.class);
    private final SqlRiskDetector detector;
    private final MonitorProperties properties;
    private final SqlRiskMonitor monitor;

    public SqlRiskExecutionGuard(SqlRiskDetector detector, MonitorProperties properties, SqlRiskMonitor monitor) {
        this.detector = Objects.requireNonNull(detector);
        this.properties = Objects.requireNonNull(properties);
        this.monitor = Objects.requireNonNull(monitor);
    }

    @Override public boolean supports(String jdbcUrlPrefix) { return "jdbc:mysql:".equals(jdbcUrlPrefix); }

    @Override
    public void check(JdbcSession session, String sql) {
        var mode = properties.getSqlRisk().getMode();
        if (mode == MonitorProperties.SqlRiskMode.OFF) return;
        SqlRiskResult result;
        RuntimeException failure = null;
        try {
            result = Objects.requireNonNull(detector.check(sql), "SqlRiskDetector 返回 null");
        } catch (RuntimeException error) {
            failure = error;
            result = SqlRiskResult.reject("SQL 检查未完成：" + error.getClass().getSimpleName());
        }
        if (result.allowed()) return;
        boolean blocked = mode == MonitorProperties.SqlRiskMode.BLOCK;
        monitor.record(session.group(), sql, blocked, failure != null, result.reasons());
        // Bounded SQL and details are available in the authenticated monitor, not in exception messages.
        LOG.warn("SQL 风险检查：group={}, action={}, inspectionFailed={}, reasons={}",
                session.group(), blocked ? "block" : "observe", failure != null, result.reasons().size());
        if (blocked) throw new SqlRiskException(result.reasons(), failure != null, failure);
    }
}

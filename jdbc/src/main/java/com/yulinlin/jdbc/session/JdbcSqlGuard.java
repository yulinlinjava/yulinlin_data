package com.yulinlin.jdbc.session;

/** Optional pre-execution SQL-template check. No bound values are exposed. Implementations must be thread-safe. */
public interface JdbcSqlGuard {
    boolean supports(String jdbcUrlPrefix);

    /** Throw a runtime exception to reject execution. Never modify SQL or the current transaction. */
    void check(JdbcSession session, String sql);
}

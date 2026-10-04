package com.yulinlin.jdbc.session;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.IdentityHashMap;
import java.util.Map;

@Slf4j
public class ConnectionUtil {
    public static final int DEFAULT_PARALLEL_CONNECTIONS = 4;
    private static volatile int core = DEFAULT_PARALLEL_CONNECTIONS;
    private static volatile boolean debug;
    private static final ThreadLocal<Map<DataSource, ConnectionPool>> legacyPools =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<Integer> legacyDepth = ThreadLocal.withInitial(() -> 0);

    public static void setDebug(boolean debug) { ConnectionUtil.debug = debug; }

    /** Stateless helper; JdbcSession owns and captures the returned pool. */
    public static ConnectionPool createPool(DataSource source, int parallelConnections) {
        return new ConnectionPool(source, parallelConnections).initialize();
    }

    // Retained for legacy callers. These no longer inspect SessionUtil or the number of route groups.
    public static ConnectionPool pool(DataSource source) {
        return legacyPools.get().computeIfAbsent(source, key -> createPool(key, core));
    }

    public static Connection getSpringConnection(DataSource source) {
        if (legacyDepth.get() > 0 || legacyPools.get().containsKey(source)) {
            return pool(source).getConnection();
        }
        return DataSourceUtils.getConnection(source);
    }

    public static void releaseSpringConnection(DataSource source, Connection connection) {
        ConnectionPool pool = legacyPools.get().get(source);
        if (pool != null) pool.releaseConnection(connection);
        else DataSourceUtils.releaseConnection(connection, source);
    }

    public static void startTransaction() { legacyDepth.set(legacyDepth.get() + 1); }
    public static void commitTransaction() { finish(false); }
    public static void rollbackTransaction() { finish(true); }

    @SneakyThrows
    private static void finish(boolean rollback) {
        int depth = legacyDepth.get();
        if (rollback) legacyPools.get().values().forEach(ConnectionPool::setRollbackOnly);
        if (depth > 1) { legacyDepth.set(depth - 1); return; }
        Throwable failure = null;
        try {
            for (ConnectionPool pool : legacyPools.get().values()) {
                try { pool.finish(rollback || failure != null); }
                catch (Throwable error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
        } finally {
            legacyPools.remove();
            legacyDepth.remove();
            if (debug) log.debug("Legacy JDBC transaction completed, rollback={}", rollback);
        }
        if (failure != null) throw failure;
    }

    public static void setCore(int core) {
        if (core < 1) throw new IllegalArgumentException("parallelConnections must be positive");
        ConnectionUtil.core = core;
    }
}

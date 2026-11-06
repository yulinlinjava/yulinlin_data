package com.yulinlin.jdbc.session;

import com.zaxxer.hikari.HikariDataSource;
import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.transaction.TransactionLog;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** 一次本地事务的一条连接及保存点。并行独立写入不使用此资源。 */
public final class JdbcTransactionResources {
    private static final class Point {
        final Savepoint value;
        boolean failed;
        Point(Savepoint value) { this.value = value; }
    }
    private final DataSource dataSource;
    private final Thread owner = Thread.currentThread();
    private final TransactionLog log = TransactionLog.current();
    private final String group;
    private final String cluster;
    private final Deque<Point> points = new ArrayDeque<>();
    private Connection connection;
    private boolean originalAutoCommit;
    private boolean borrowed;
    private boolean rollbackOnly;
    private boolean closed;

    public JdbcTransactionResources(DataSource source) { this(source, "unknown", "unknown"); }
    public JdbcTransactionResources(DataSource source, String group, String cluster) {
        this.dataSource = targetDataSource(Objects.requireNonNull(source, "dataSource"));
        this.group = group;
        this.cluster = cluster;
    }

    static int connectionLimit(DataSource source, int requested) {
        if (requested < 1) throw new IllegalArgumentException("parallelConnections must be positive");
        return source instanceof HikariDataSource pool ? Math.min(requested, Math.max(1, pool.getMaximumPoolSize())) : requested;
    }

    static DataSource targetDataSource(DataSource source) {
        while (source instanceof TransactionAwareDataSourceProxy proxy)
            source = Objects.requireNonNull(proxy.getTargetDataSource(), "targetDataSource");
        return source;
    }

    public JdbcTransactionResources initialize() {
        usable();
        if (connection != null) return this;
        try {
            connection = dataSource.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            if (originalAutoCommit) connection.setAutoCommit(false);
            event("JDBC事务连接已取得");
            return this;
        } catch (SQLException error) {
            rollbackOnly = true;
            if (connection != null) {
                try { connection.close(); } catch (SQLException cleanup) { error.addSuppressed(cleanup); }
                connection = null;
            }
            throw JdbcErrors.translate(error);
        }
    }

    private void active() {
        if (Thread.currentThread() != owner) throw new OrmException("事务连接不能跨业务线程使用");
        if (closed) throw new OrmException("JDBC 事务已经结束");
    }
    private void usable() {
        active();
        if (isRollbackOnly()) throw new OrmException("当前事务范围只能回滚，不能继续执行 SQL");
    }
    public boolean isRollbackOnly() { return rollbackOnly || (!points.isEmpty() && points.getLast().failed); }
    public void setRollbackOnly() { rollbackOnly = true; event("JDBC事务已标记为只能回滚"); }

    public Connection getConnection() {
        initialize();
        if (borrowed) throw new OrmException("事务连接正在使用，不允许并行执行 SQL");
        try {
            if (connection.isClosed()) throw new SQLException("事务连接已关闭", "08003");
        } catch (SQLException error) {
            rollbackOnly = true;
            throw JdbcErrors.translate(error);
        }
        borrowed = true;
        return connection;
    }
    public void releaseConnection(Connection value) {
        active();
        if (value != connection || !borrowed) throw new OrmException("连接不属于当前借用范围");
        borrowed = false;
    }

    public Object createSavepoint() {
        initialize();
        if (borrowed) throw new OrmException("SQL 执行期间不能创建保存点");
        try {
            if (!connection.getMetaData().supportsSavepoints())
                throw new OrmException("当前 JDBC 驱动不支持保存点");
            Point point = new Point(connection.setSavepoint());
            points.addLast(point);
            event("JDBC保存点已创建");
            return point;
        } catch (SQLException error) {
            rollbackOnly = true;
            throw JdbcErrors.translate(error);
        }
    }
    private Point point(Object value) {
        active();
        if (borrowed || points.isEmpty() || points.getLast() != value)
            throw new OrmException("保存点必须在原线程按嵌套顺序结束");
        return points.getLast();
    }
    public void releaseSavepoint(Object value) {
        Point point = point(value);
        usable();
        try {
            connection.releaseSavepoint(point.value);
            points.removeLast();
            event("JDBC保存点已释放");
        } catch (SQLException error) {
            rollbackOnly = true;
            throw JdbcErrors.translate(error);
        }
    }
    public void rollbackToSavepoint(Object value) {
        Point point = point(value);
        try {
            if (rollbackOnly) throw new OrmException("物理事务已失效，不能通过保存点恢复");
            connection.rollback(point.value);
            connection.releaseSavepoint(point.value);
            event("JDBC保存点回滚成功");
        } catch (Throwable error) {
            rollbackOnly = true;
            throw JdbcErrors.translate(error);
        } finally {
            points.removeLast();
        }
    }

    /** PG 普通错误可由当前保存点恢复；不可恢复的错误始终使物理事务失效。 */
    void executionFailed(Connection value, Throwable error) {
        try {
            boolean sqlError = false, unknownState = false, lockTimeout = false;
            var pending = new ArrayDeque<Throwable>();
            var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
            pending.add(error);
            while (!pending.isEmpty()) {
                Throwable current = pending.removeFirst();
                if (!seen.add(current)) continue;
                if (current instanceof Error || current instanceof SQLNonTransientConnectionException
                        || current instanceof SQLTransientConnectionException || current instanceof SQLRecoverableException
                        || current instanceof SQLTimeoutException) { setRollbackOnly(); return; }
                if (current instanceof SQLException sql) {
                    sqlError = true;
                    String state = sql.getSQLState();
                    if (state != null && (state.startsWith("08") || state.startsWith("40")
                            || state.startsWith("HYT") || (state.startsWith("25") && !state.equals("25P02")))) {
                        setRollbackOnly(); return;
                    }
                    unknownState |= (state == null || state.isBlank()) && sql.getNextException() == null
                            && !(sql.getCause() instanceof SQLException);
                    lockTimeout |= sql.getErrorCode() == 1205;
                    if (sql.getNextException() != null) pending.add(sql.getNextException());
                }
                if (current.getCause() != null) pending.add(current.getCause());
                Collections.addAll(pending, current.getSuppressed());
            }
            if (!sqlError) return;
            if (unknownState || value.isClosed() || value.getAutoCommit()) { setRollbackOnly(); return; }
            String product = value.getMetaData().getDatabaseProductName();
            if (product == null || product.isBlank()
                    || (lockTimeout && ("MySQL".equalsIgnoreCase(product) || "MariaDB".equalsIgnoreCase(product)))) {
                setRollbackOnly();
            } else if ("PostgreSQL".equalsIgnoreCase(product)) {
                if (points.isEmpty()) setRollbackOnly();
                else points.getLast().failed = true;
            }
        } catch (Throwable checkFailure) {
            if (checkFailure != error) error.addSuppressed(checkFailure);
            setRollbackOnly();
        }
    }

    public void finish(boolean rollback) {
        active();
        if (borrowed) throw new OrmException("事务连接仍在执行 SQL，不能结束事务");
        Throwable failure = null;
        boolean mustRollback = rollback || isRollbackOnly() || !points.isEmpty();
        try {
            if (connection != null) {
                try {
                    if (mustRollback) { connection.rollback(); event("JDBC连接回滚成功"); }
                    else { connection.commit(); event("JDBC连接提交成功"); }
                } catch (Throwable error) {
                    failure = error;
                    if (!mustRollback) {
                        try { connection.rollback(); } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
                    }
                }
            }
            if (!rollback && mustRollback && failure == null) failure = new OrmException("当前事务只能回滚，不能提交");
        } finally {
            if (connection != null) {
                try {
                    // 提交结果不明时不切换自动提交，避免意外提交。
                    if (failure == null && !connection.isClosed() && connection.getAutoCommit() != originalAutoCommit)
                        connection.setAutoCommit(originalAutoCommit);
                } catch (Throwable cleanup) { failure = append(failure, cleanup); }
                try { connection.close(); event("JDBC事务连接已释放"); }
                catch (Throwable cleanup) { failure = append(failure, cleanup); }
            }
            connection = null;
            points.clear();
            closed = true;
        }
        if (failure != null) {
            log.error("JDBC事务完成失败", "group=" + TransactionLog.token(group)
                    + " cluster=" + TransactionLog.token(cluster) + " rollback=" + rollback, failure);
            throw JdbcErrors.translate(failure);
        }
    }

    private static Throwable append(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    private void event(String message) {
        log.info(message, "group=" + TransactionLog.token(group) + " cluster=" + TransactionLog.token(cluster)
                + " owner=orm savepoints=" + points.size() + " rollbackOnly=" + isRollbackOnly());
    }
}

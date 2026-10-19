package com.yulinlin.jdbc.session;

import com.zaxxer.hikari.HikariDataSource;
import com.yulinlin.data.core.transaction.TransactionLog;
import lombok.SneakyThrows;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** A bounded set of connections owned by ONE session transaction, not a global connection pool. */
public class ConnectionPool {
    private static final class Node {
        final Connection connection;
        final boolean originalAutoCommit;
        boolean available = true;
        Node(Connection connection, boolean originalAutoCommit) {
            this.connection = connection;
            this.originalAutoCommit = originalAutoCommit;
        }
    }

    private final DataSource dataSource;
    private final TransactionLog transactionLog;
    private final String group;
    private final String cluster;
    private final List<Node> nodes = new ArrayList<>();
    private final int limit;
    private boolean initialized;
    private int creating;
    private boolean closed;
    private volatile boolean rollbackOnly;

    public ConnectionPool(DataSource dataSource, int length) {
        this(dataSource, length, "unknown", "unknown");
    }

    public ConnectionPool(DataSource dataSource, int length, String group, String cluster) {
        this.dataSource = targetDataSource(Objects.requireNonNull(dataSource, "dataSource"));
        this.transactionLog = TransactionLog.current();
        this.group = group;
        this.cluster = cluster;
        if (length < 1) throw new IllegalArgumentException("parallelConnections must be positive");
        limit = this.dataSource instanceof HikariDataSource hikari
                ? Math.min(length, Math.max(1, hikari.getMaximumPoolSize())) : length;
    }

    static DataSource targetDataSource(DataSource source) {
        // 事务感知代理会复用线程连接；框架自管时必须能够取得独立连接。
        while (source instanceof TransactionAwareDataSourceProxy proxy) {
            source = Objects.requireNonNull(proxy.getTargetDataSource(), "targetDataSource");
        }
        return source;
    }

    /** Must be called on the owning thread, BEFORE handing the pool to asynchronous workers. */
    @SneakyThrows
    public synchronized ConnectionPool initialize() {
        if (closed) throw new IllegalStateException("Transaction connections already closed");
        if (initialized) return this;
        jdbcLog("JDBC事务开始获取连接", null, "", null);
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean autoCommit = connection.getAutoCommit();
            if (autoCommit) connection.setAutoCommit(false);
            nodes.add(new Node(connection, autoCommit));
            initialized = true;
            if (transactionLog.isEnabled()) jdbcLog("JDBC事务连接已加入", connection,
                    "originalAutoCommit=" + autoCommit + " autoCommit=false", null);
            return this;
        } catch (Throwable error) {
            jdbcLog("JDBC事务连接初始化失败", connection, "", error);
            try {
                if (connection != null) connection.close();
            }
            catch (Throwable cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public synchronized int getLimit() { return limit; }
    /** @deprecated 连接现统一由框架管理；仅保留旧调用的兼容方法。 */
    @Deprecated
    public boolean isSpringManaged() { return false; }
    public boolean isRollbackOnly() { return rollbackOnly; }
    public void setRollbackOnly() {
        boolean first = !rollbackOnly;
        rollbackOnly = true;
        if (first) jdbcLog("JDBC事务已标记为只能回滚", null, "", null);
    }

    @SneakyThrows
    public Connection getConnection() {
        synchronized (this) {
            if (!initialized) initialize();
            for (;;) {
                if (closed) throw new IllegalStateException("Transaction connections already closed");
                for (Node node : nodes) {
                    if (node.available) {
                        if (node.connection.isClosed()) throw new IllegalStateException("Transaction connection is closed");
                        node.available = false;
                        return node.connection;
                    }
                }
                if (nodes.size() + creating < limit) {
                    creating++;
                    break;
                }
                try { wait(); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    rollbackOnly = true;
                    throw error;
                }
            }
        }
        // A depleted DataSource can block here. Do not hold the monitor needed by existing borrowers.
        Connection connection = null;
        try {
            jdbcLog("JDBC并行事务开始获取连接", null, "", null);
            connection = dataSource.getConnection();
            boolean autoCommit = connection.getAutoCommit();
            if (autoCommit) connection.setAutoCommit(false);
            Node node = new Node(connection, autoCommit);
            node.available = false;
            synchronized (this) { nodes.add(node); }
            if (transactionLog.isEnabled()) jdbcLog("JDBC并行事务连接已加入", connection,
                    "originalAutoCommit=" + autoCommit + " autoCommit=false", null);
            return connection;
        } catch (Throwable error) {
            jdbcLog("JDBC并行事务获取连接失败", connection, "", error);
            rollbackOnly = true;
            if (connection != null) {
                try { connection.close(); }
                catch (Throwable cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            }
            throw error;
        } finally {
            synchronized (this) { creating--; notifyAll(); }
        }
    }

    public Connection getJdbcConnection() { return getConnection(); }

    public synchronized void releaseConnection(Connection connection) {
        for (Node node : nodes) {
            if (node.connection == connection) {
                node.available = true;
                notifyAll();
                return;
            }
        }
        throw new IllegalArgumentException("Connection does not belong to this transaction");
    }

    public synchronized Collection<Connection> getConnections() {
        return nodes.stream().map(node -> node.connection).toList();
    }

    /** Waits for borrowers, attempts completion, always releases every connection, propagates errors. */
    @SneakyThrows
    public synchronized void finish(boolean rollback) {
        if (closed) return;
        jdbcLog(rollback || rollbackOnly ? "JDBC事务准备回滚" : "JDBC事务准备提交", null, "", null);
        boolean interrupted = false;
        while (creating > 0 || nodes.stream().anyMatch(node -> !node.available)) {
            try { wait(); } catch (InterruptedException ignored) { interrupted = true; }
        }
        closed = true;
        Throwable failure = null;
        boolean mustRollback = rollback || rollbackOnly;
        try {
            if (nodes.isEmpty()) jdbcLog("JDBC事务未借用连接，无需物理提交或回滚", null, "", null);
            for (Node node : nodes) {
                boolean doRollback = mustRollback || failure != null;
                try {
                    jdbcLog(doRollback ? "JDBC连接开始回滚" : "JDBC连接开始提交", node.connection, "", null);
                    if (doRollback) node.connection.rollback();
                    else node.connection.commit();
                    jdbcLog(doRollback ? "JDBC连接回滚成功" : "JDBC连接提交成功", node.connection, "", null);
                } catch (Throwable error) {
                    jdbcLog(doRollback ? "JDBC连接回滚失败" : "JDBC连接提交失败", node.connection, "", error);
                    failure = append(failure, error);
                    if (!doRollback) {
                        try {
                            node.connection.rollback();
                            jdbcLog("JDBC提交失败后的回滚成功", node.connection, "", null);
                        }
                        catch (Throwable cleanup) {
                            jdbcLog("JDBC提交失败后的回滚也失败", node.connection, "", cleanup);
                            failure = append(failure, cleanup);
                        }
                    }
                }
            }
            if (!rollback && rollbackOnly) {
                failure = append(failure, new IllegalStateException("JDBC transaction was marked rollback-only"));
            }
        } finally {
            for (Node node : nodes) {
                try {
                    // If completion failed, never switch autoCommit on (that could commit pending data).
                    if (failure == null && node.connection.getAutoCommit() != node.originalAutoCommit) {
                        node.connection.setAutoCommit(node.originalAutoCommit);
                    }
                } catch (Throwable cleanup) {
                    jdbcLog("JDBC连接恢复自动提交状态失败", node.connection, "", cleanup);
                    failure = append(failure, cleanup);
                }
                finally {
                    try {
                        node.connection.close();
                        jdbcLog("JDBC框架连接已释放", node.connection, "", null);
                    }
                    catch (Throwable cleanup) {
                        jdbcLog("JDBC连接释放失败", node.connection, "", cleanup);
                        failure = append(failure, cleanup);
                    }
                }
            }
            nodes.clear();
            notifyAll();
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (failure != null) throw failure;
    }

    /** Legacy cleanup entry point rolls back, rather than discarding uncommitted work. */
    public void clear() { finish(true); }

    private void jdbcLog(String event, Connection connection, String extra, Throwable error) {
        if (!transactionLog.isEnabled()) return;
        int count;
        synchronized (this) { count = nodes.size(); }
        String details = "group=" + TransactionLog.token(group) + " cluster=" + TransactionLog.token(cluster)
                + " dataSource=" + dataSource.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(dataSource))
                + " connection=" + (connection == null ? "none" : Integer.toHexString(System.identityHashCode(connection)))
                + " owner=framework"
                + " connections=" + count + " rollbackOnly=" + rollbackOnly;
        if (!extra.isEmpty()) details += " " + extra;
        if (error == null) transactionLog.info(event, details);
        else transactionLog.error(event, details, error);
    }

    private static Throwable append(Throwable failure, Throwable error) {
        if (failure == null) return error;
        if (failure != error) failure.addSuppressed(error);
        return failure;
    }
}

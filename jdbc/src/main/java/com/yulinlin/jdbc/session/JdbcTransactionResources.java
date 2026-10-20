package com.yulinlin.jdbc.session;

import com.zaxxer.hikari.HikariDataSource;
import com.yulinlin.data.core.transaction.TransactionLog;
import lombok.SneakyThrows;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一个 Session 事务的连接资源，不是替代 Hikari/Druid 的连接池，也不管理业务事务嵌套。
 * 首条连接由所属线程初始化；额外连接按需异步申请，执行线程始终优先复用归还的连接。
 */
public final class JdbcTransactionResources {
    private enum State { ACTIVE, FINISHING, CLOSED }

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
    private State state = State.ACTIVE;
    private Thread expanding;
    private boolean expansionDisabled;
    private Throwable acquisitionFailure;
    private volatile boolean rollbackOnly;

    public JdbcTransactionResources(DataSource dataSource, int length) {
        this(dataSource, length, "unknown", "unknown");
    }

    public JdbcTransactionResources(DataSource dataSource, int length, String group, String cluster) {
        this.dataSource = targetDataSource(Objects.requireNonNull(dataSource, "dataSource"));
        this.transactionLog = TransactionLog.current();
        this.group = group;
        this.cluster = cluster;
        limit = connectionLimit(this.dataSource, length);
    }

    static int connectionLimit(DataSource source, int length) {
        if (length < 1) throw new IllegalArgumentException("parallelConnections must be positive");
        return source instanceof HikariDataSource hikari
                ? Math.min(length, Math.max(1, hikari.getMaximumPoolSize())) : length;
    }

    static DataSource targetDataSource(DataSource source) {
        // 事务感知代理会复用线程连接；框架自管时必须能够取得独立连接。
        while (source instanceof TransactionAwareDataSourceProxy proxy) {
            source = Objects.requireNonNull(proxy.getTargetDataSource(), "targetDataSource");
        }
        return source;
    }

    /** 必须先在事务所属线程调用，再交给批处理工作线程。未使用资源的事务不借连接。 */
    @SneakyThrows
    public synchronized JdbcTransactionResources initialize() {
        requireActive();
        if (acquisitionFailure != null) throw acquisitionFailure;
        if (!nodes.isEmpty()) return this;
        jdbcLog("JDBC事务开始获取连接", null, "", null);
        try {
            nodes.add(openConnection());
            return this;
        } catch (Throwable error) {
            rollbackOnly = true;
            acquisitionFailure = error;
            jdbcLog("JDBC事务连接初始化失败", null, "", error);
            throw error;
        }
    }

    /** 首条连接和并行扩容使用同一套配置、失败清理逻辑。 */
    private Node openConnection() throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean autoCommit = connection.getAutoCommit();
            if (autoCommit) connection.setAutoCommit(false);
            Node node = new Node(connection, autoCommit);
            if (transactionLog.isEnabled()) jdbcLog("JDBC事务连接已取得", connection,
                    "originalAutoCommit=" + autoCommit + " autoCommit=false", null);
            return node;
        } catch (Throwable error) {
            try {
                if (connection != null) connection.close();
            }
            catch (Throwable cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public int getLimit() { return limit; }
    public boolean isRollbackOnly() { return rollbackOnly; }
    public void setRollbackOnly() {
        boolean first = !rollbackOnly;
        rollbackOnly = true;
        if (first) jdbcLog("JDBC事务已标记为只能回滚", null, "", null);
    }

    @SneakyThrows
    public synchronized Connection getConnection() {
        requireActive();
        if (acquisitionFailure != null) throw acquisitionFailure;
        if (nodes.isEmpty()) throw new IllegalStateException("请先在事务所属线程初始化 JDBC 连接资源");
        for (;;) {
            requireActive();
            if (acquisitionFailure != null) throw acquisitionFailure;
            for (Node node : nodes) {
                if (node.available) {
                    if (node.connection.isClosed()) {
                        rollbackOnly = true;
                        throw new IllegalStateException("事务连接已关闭");
                    }
                    node.available = false;
                    return node.connection;
                }
            }
            expandIfNeeded();
            try { wait(); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                rollbackOnly = true;
                throw error;
            }
        }
    }

    /** 调用时持有锁；每个事务最多一个正在申请的额外连接，不为每个等待者创建线程。 */
    private void expandIfNeeded() {
        if (expansionDisabled || expanding != null || nodes.size() >= limit) return;
        try {
            expanding = Thread.ofVirtual().name("jdbc-expand-" + transactionLog.id())
                    .inheritInheritableThreadLocals(false).unstarted(this::expand);
            expanding.start();
        } catch (Throwable error) {
            expanding = null;
            rollbackOnly = true;
            acquisitionFailure = error;
            throw error;
        }
    }

    private void expand() {
        Node acquired = null;
        try {
            jdbcLog("JDBC并行事务开始获取连接", null, "", null);
            acquired = openConnection();
            synchronized (this) {
                if (state == State.ACTIVE) {
                    nodes.add(acquired);
                    acquired = null; // 交给事务收尾，扩容线程不再负责关闭。
                }
            }
        } catch (SQLException error) {
            synchronized (this) {
                // 额外连接只是性能优化，池耗尽等借连接失败可退化为复用已有连接。
                expansionDisabled = true;
                if (state == State.ACTIVE)
                    jdbcLog("JDBC扩容失败，本事务继续复用已有连接", null, "", error);
            }
        } catch (Throwable error) {
            synchronized (this) {
                if (state == State.ACTIVE) {
                    acquisitionFailure = error;
                    rollbackOnly = true;
                }
            }
            jdbcLog("JDBC扩容异常", null, "", error);
            if (error instanceof Error fatal) throw fatal;
        } finally {
            // 驱动可能不响应中断：晚到连接从未执行业务 SQL，独立关闭，不能加入已结束事务。
            if (acquired != null) closeUnused(acquired);
            synchronized (this) { expanding = null; notifyAll(); }
        }
    }

    private void closeUnused(Node node) {
        Throwable failure = null;
        try {
            node.connection.rollback();
            if (node.originalAutoCommit) node.connection.setAutoCommit(true);
        } catch (Throwable error) { failure = error; }
        finally {
            try { node.connection.close(); }
            catch (Throwable error) { failure = append(failure, error); }
        }
        jdbcLog(failure == null ? "JDBC未使用的扩容连接已释放" : "JDBC未使用的扩容连接释放失败",
                node.connection, "", failure);
    }

    public synchronized void releaseConnection(Connection connection) {
        for (Node node : nodes) {
            if (node.connection == connection) {
                if (node.available) throw new IllegalStateException("事务连接不能重复归还");
                node.available = true;
                notifyAll();
                return;
            }
        }
        throw new IllegalArgumentException("连接不属于本次事务");
    }

    private void requireActive() {
        if (state != State.ACTIVE) throw new IllegalStateException("事务正在结束或已关闭，不允许继续借用连接");
    }

    /** 停止借用、等待执行者归还、完成物理事务并释放全部连接；异常保留并向外传播。 */
    @SneakyThrows
    public synchronized void finish(boolean rollback) {
        if (state == State.CLOSED) return;
        requireActive();
        state = State.FINISHING;
        // 不等尚未借到的扩容连接，否则外部池耗尽时，本事务会一直占着已有连接无法提交。
        if (expanding != null) expanding.interrupt();
        notifyAll();
        jdbcLog(rollback || rollbackOnly ? "JDBC事务准备回滚" : "JDBC事务准备提交", null, "", null);
        boolean interrupted = false;
        while (nodes.stream().anyMatch(node -> !node.available)) {
            try { wait(); } catch (InterruptedException ignored) { interrupted = true; }
        }
        Throwable failure = acquisitionFailure;
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
                failure = append(failure, new IllegalStateException("JDBC 事务已被标记为只能回滚"));
            }
        } finally {
            for (Node node : nodes) {
                try {
                    // 完成失败时不能打开自动提交，否则可能把未完成的数据意外提交。
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
            state = State.CLOSED;
            notifyAll();
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (failure != null) throw failure;
    }

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

package com.yulinlin.jdbc.session;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final List<Node> nodes = new ArrayList<>();
    private int limit;
    private boolean initialized;
    private int creating;
    private volatile boolean springManaged;
    private ConnectionHolder springHolder;
    private boolean closed;
    private volatile boolean rollbackOnly;

    public ConnectionPool(DataSource dataSource, int length) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (length < 1) throw new IllegalArgumentException("parallelConnections must be positive");
        limit = dataSource instanceof HikariDataSource hikari
                ? Math.min(length, Math.max(1, hikari.getMaximumPoolSize())) : length;
    }

    /** Must be called on the owning thread, BEFORE handing the pool to asynchronous workers. */
    @SneakyThrows
    public synchronized ConnectionPool initialize() {
        if (closed) throw new IllegalStateException("Transaction connections already closed");
        if (initialized) return this;
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            springManaged = DataSourceUtils.isConnectionTransactional(connection, dataSource);
            if (springManaged && TransactionSynchronizationManager.getResource(dataSource) instanceof ConnectionHolder holder) {
                springHolder = holder;
            }
            boolean autoCommit = connection.getAutoCommit();
            if (springManaged) limit = 1;
            else if (autoCommit) connection.setAutoCommit(false);
            nodes.add(new Node(connection, autoCommit));
            initialized = true;
            return this;
        } catch (Throwable error) {
            try { DataSourceUtils.doReleaseConnection(connection, dataSource); }
            catch (Throwable cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public synchronized int getLimit() { return limit; }
    public synchronized boolean isSpringManaged() { return springManaged; }
    public boolean isRollbackOnly() { return rollbackOnly; }
    public void setRollbackOnly() {
        rollbackOnly = true;
        // Mark Spring immediately: its interceptor may finish BEFORE the route interceptor.
        if (springManaged && springHolder != null
                && TransactionSynchronizationManager.getResource(dataSource) == springHolder) {
            springHolder.setRollbackOnly();
        }
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
            connection = dataSource.getConnection();
            boolean autoCommit = connection.getAutoCommit();
            if (autoCommit) connection.setAutoCommit(false);
            Node node = new Node(connection, autoCommit);
            node.available = false;
            synchronized (this) { nodes.add(node); }
            return connection;
        } catch (Throwable error) {
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
        boolean interrupted = false;
        while (creating > 0 || nodes.stream().anyMatch(node -> !node.available)) {
            try { wait(); } catch (InterruptedException ignored) { interrupted = true; }
        }
        closed = true;
        Throwable failure = null;
        boolean mustRollback = rollback || rollbackOnly;
        try {
            if (springManaged) {
                if (mustRollback && TransactionSynchronizationManager.getResource(dataSource) instanceof ConnectionHolder holder) {
                    holder.setRollbackOnly();
                }
            } else {
                for (Node node : nodes) {
                    boolean doRollback = mustRollback || failure != null;
                    try {
                        if (doRollback) node.connection.rollback();
                        else node.connection.commit();
                    } catch (Throwable error) {
                        failure = append(failure, error);
                        if (!doRollback) {
                            try { node.connection.rollback(); }
                            catch (Throwable cleanup) { failure = append(failure, cleanup); }
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
                    if (!springManaged && failure == null && node.connection.getAutoCommit() != node.originalAutoCommit) {
                        node.connection.setAutoCommit(node.originalAutoCommit);
                    }
                } catch (Throwable cleanup) { failure = append(failure, cleanup); }
                finally {
                    try {
                        // An outer framework aspect may finish after Spring has already closed/unbound it.
                        if (!springManaged || (springHolder != null
                                && TransactionSynchronizationManager.getResource(dataSource) == springHolder)) {
                            DataSourceUtils.doReleaseConnection(node.connection, dataSource);
                        }
                    }
                    catch (Throwable cleanup) { failure = append(failure, cleanup); }
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

    private static Throwable append(Throwable failure, Throwable error) {
        if (failure == null) return error;
        if (failure != error) failure.addSuppressed(error);
        return failure;
    }
}

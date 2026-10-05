package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.session.ConnectionPool;
import com.yulinlin.jdbc.session.JdbcSession;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared JDBC execution with on-demand local tables. Never borrows a second schema connection. */
public class SqliteSession extends JdbcSession {
    private record EntityType(Class<?> type, boolean underscore) { }
    private static final class SchemaChecks {
        final Set<EntityType> checked = new HashSet<>();
        boolean springManaged;
    }

    private final SqliteSchemaManager schemaManager = new SqliteSchemaManager();
    private final Set<EntityType> committedChecks = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<SchemaChecks> localChecks = new ThreadLocal<>();
    private final Object springChecksKey = new Object();

    public SqliteSession(DataSource dataSource) {
        super(dataSource);
        setParseManager(new SqliteParseManager());
        setParallelConnections(1);
    }

    @Override public void setProperties(JdbcProperties properties) {
        super.setProperties(properties);
        setParallelConnections(1);
    }

    @Override public boolean supportsParallelWrites() { return false; }

    @Override protected void beforeExecute(BaseRequest<?> request) {
        Class<?> fromClass = request.getFromClass();
        if (fromClass == null || fromClass == Object.class) return;
        EntityType entity = new EntityType(fromClass, isMapUnderscoreToCamelCase());
        if (committedChecks.contains(entity)
                || !schemaManager.isTableEntity(fromClass, entity.underscore())) return;

        SchemaChecks local = localChecks.get();
        if (local != null && local.checked.contains(entity)) return;
        ConnectionPool pool = transactionConnections();
        Connection connection = pool.getConnection();
        try {
            // Another caller may have completed initialization while this caller waited for the pool.
            if (committedChecks.contains(entity)) return;
            SchemaChecks checks = local;
            if (DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                if (local != null) local.springManaged = true;
                // Without a completion callback, do not retain a potentially uncommitted result.
                checks = TransactionSynchronizationManager.isSynchronizationActive() ? springChecks() : null;
            }
            if (checks != null && checks.checked.contains(entity)) return;
            schemaManager.ensureTable(connection, fromClass, entity.underscore());
            if (checks != null) checks.checked.add(entity);
        } finally {
            pool.releaseConnection(connection);
        }
    }

    @Override public void startTransaction() {
        if (!isOpenTransaction()) localChecks.set(new SchemaChecks());
        super.startTransaction();
    }

    @Override public void commitTransaction() {
        SchemaChecks checks = localChecks.get();
        try {
            super.commitTransaction();
            if (!isOpenTransaction() && checks != null && !checks.springManaged) {
                committedChecks.addAll(checks.checked);
            }
        } finally {
            if (!isOpenTransaction()) localChecks.remove();
        }
    }

    @Override public void rollbackTransaction() {
        try { super.rollbackTransaction(); }
        finally { if (!isOpenTransaction()) localChecks.remove(); }
    }

    private SchemaChecks springChecks() {
        Object resource = TransactionSynchronizationManager.getResource(springChecksKey);
        if (resource instanceof SchemaChecks checks) return checks;
        SchemaChecks checks = new SchemaChecks();
        checks.springManaged = true;
        TransactionSynchronizationManager.bindResource(springChecksKey, checks);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void suspend() {
                TransactionSynchronizationManager.unbindResource(springChecksKey);
            }

            @Override public void resume() {
                TransactionSynchronizationManager.bindResource(springChecksKey, checks);
            }

            @Override public void afterCompletion(int status) {
                try {
                    if (status == STATUS_COMMITTED) committedChecks.addAll(checks.checked);
                } finally {
                    checks.checked.clear();
                    if (TransactionSynchronizationManager.getResource(springChecksKey) == checks) {
                        TransactionSynchronizationManager.unbindResource(springChecksKey);
                    }
                }
            }
        });
        return checks;
    }
}

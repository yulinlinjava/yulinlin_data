package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.jdbc.session.JdbcSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared JDBC execution with H2 SQL and on-demand local tables. */
public class H2Session extends JdbcSession {
    private record EntityType(Class<?> type, boolean underscore) { }

    private final H2SchemaManager schemaManager = new H2SchemaManager();
    private final Set<EntityType> checkedTables = ConcurrentHashMap.newKeySet();
    private volatile DataSource schemaDataSource;
    private volatile H2Properties.SchemaMode schemaMode = H2Properties.SchemaMode.CREATE;

    public H2Session(DataSource dataSource) {
        super(dataSource);
        schemaDataSource = dataSource instanceof H2DataSource h2
                ? h2.directDataSource() : dataSource;
        setParseManager(new H2ParseManager());
    }

    void configure(H2Properties properties, DataSource schemaDataSource) {
        this.schemaDataSource = schemaDataSource;
        schemaMode = properties.getSchemaMode();
        setParallelConnections(properties.getMaxConnections());
        setExecuteBatchSize(properties.getBatchSize());
    }

    @Override protected void beforeExecute(BaseRequest<?> request) {
        Class<?> fromClass = request.getFromClass();
        if (fromClass == null || fromClass == Object.class || schemaMode == H2Properties.SchemaMode.NONE) return;
        EntityType entity = new EntityType(fromClass, isMapUnderscoreToCamelCase());
        if (checkedTables.contains(entity)
                || !schemaManager.isTableEntity(fromClass, entity.underscore())) return;

        // H2 DDL commits its own connection. A dedicated direct connection prevents an implicit
        // commit of the caller's business transaction and avoids consuming its bounded worker pool.
        try (Connection connection = schemaDataSource.getConnection()) {
            schemaManager.ensureTable(connection, fromClass, entity.underscore(), schemaMode);
            checkedTables.add(entity);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize H2 table for " + fromClass.getName(), error);
        }
    }
}

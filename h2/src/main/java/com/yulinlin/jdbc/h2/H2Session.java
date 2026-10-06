package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.session.JdbcSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

/** Shared JDBC execution with H2 SQL and startup schema initialization. */
public class H2Session extends JdbcSession {
    private final H2SchemaManager schemaManager = new H2SchemaManager();
    private volatile DataSource schemaDataSource;
    private volatile SchemaMode schemaMode = SchemaMode.CREATE;

    public H2Session(DataSource dataSource) {
        super(dataSource);
        schemaDataSource = dataSource instanceof H2DataSource h2
                ? h2.directDataSource() : dataSource;
        setParseManager(new H2ParseManager());
    }

    void configure(H2Properties properties, DataSource schemaDataSource) {
        this.schemaDataSource = schemaDataSource;
        schemaMode = properties.getSchemaMode();
        setParallelConnections(properties.getParallelConnections());
        setExecuteBatchSize(properties.getExecuteBatchSize());
    }

    @Override
    public List<String> createTableSql(Class<?> entityClass) {
        return schemaManager.createTableSql(entityClass, isMapUnderscoreToCamelCase());
    }

    /** Creates or validates every scanned schema owner before the Session bean is published. */
    @Override
    public void initializeSchema(Collection<Class<?>> entities) {
        if (schemaMode == SchemaMode.NONE
                || entities == null || entities.isEmpty()) return;
        try (Connection connection = schemaDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (Class<?> entity : entities) {
                schemaManager.ensureTable(connection, entity, isMapUnderscoreToCamelCase(), schemaMode,
                        sql -> executeSchemaSql(statement, sql));
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize H2 schema", error);
        }
    }
}

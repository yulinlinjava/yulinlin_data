package com.yulinlin.jdbc.sqlite;

import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.session.JdbcSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

/** Shared JDBC execution with SQLite SQL and startup schema initialization. */
public class SqliteSession extends JdbcSession {
    private final SqliteSchemaManager schemaManager = new SqliteSchemaManager();

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

    @Override
    public List<String> createTableSql(Class<?> entityClass) {
        return schemaManager.createTableSql(entityClass, isMapUnderscoreToCamelCase());
    }

    /** Creates/validates every scanned schema owner before the Session bean is published. */
    @Override
    public void initializeSchema(Collection<Class<?>> entities) {
        if (entities == null || entities.isEmpty()) return;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (Class<?> entity : entities) {
                schemaManager.ensureTable(connection, entity, isMapUnderscoreToCamelCase(),
                        sql -> executeSchemaSql(statement, sql));
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize SQLite schema", error);
        }
    }
}

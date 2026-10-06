package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.session.JdbcSession;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.List;

/** Shared JDBC execution with PostgreSQL parsers and driver-specific value handling. */
public class PostgresqlSession extends JdbcSession {
    private final PostgresqlSchemaManager schemaManager = new PostgresqlSchemaManager();
    private volatile PostgresqlProperties.SchemaMode schemaMode = PostgresqlProperties.SchemaMode.NONE;

    public PostgresqlSession(DataSource dataSource) {
        super(dataSource);
        setParseManager(new PostgresqlParseManager());
    }

    void configure(PostgresqlProperties properties) {
        if (properties == null || properties.getSchemaMode() == null) {
            throw new IllegalArgumentException("Invalid yulinlin.postgresql configuration");
        }
        schemaMode = properties.getSchemaMode();
    }

    @Override
    public List<String> createTableSql(Class<?> entityClass) {
        return schemaManager.createTableSql(entityClass, isMapUnderscoreToCamelCase());
    }

    /** Uses a dedicated startup connection, never a business transaction connection. */
    @Override
    public void initializeSchema(Collection<Class<?>> entities) {
        if (schemaMode == PostgresqlProperties.SchemaMode.NONE || entities == null || entities.isEmpty()) return;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (Class<?> entity : entities) {
                schemaManager.ensureTable(connection, entity, isMapUnderscoreToCamelCase(), schemaMode,
                        sql -> executeSchemaSql(statement, sql));
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize PostgreSQL schema", error);
        }
    }


    @Override protected Object readColumn(ResultSet rows, String label, int jdbcType) throws SQLException {
        if (jdbcType == Types.BOOLEAN || jdbcType == Types.BIT) {
            boolean value = rows.getBoolean(label);
            return rows.wasNull() ? null : value;
        }
        return rows.getString(label);
    }
}

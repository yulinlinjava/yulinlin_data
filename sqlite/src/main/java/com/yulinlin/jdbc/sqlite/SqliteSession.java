package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.session.JdbcSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/** Shared JDBC execution with SQLite SQL and startup schema initialization. */
public class SqliteSession extends JdbcSession {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final SqliteSchemaManager schemaManager = new SqliteSchemaManager();
    private volatile SchemaMode schemaMode = SchemaMode.CREATE;

    public SqliteSession(DataSource dataSource) {
        super(dataSource);
        setParseManager(new SqliteParseManager());
        setParallelConnections(1);
    }

    @Override public void setProperties(JdbcSessionProperties properties) {
        super.setProperties(properties);
        setParallelConnections(1);
    }

    void configure(SqliteProperties properties) {
        if (properties == null || properties.getSchemaMode() == null) {
            throw new IllegalArgumentException("Invalid yulinlin.sqlite configuration");
        }
        schemaMode = properties.getSchemaMode();
        setParallelConnections(1);
    }

    @Override public boolean supportsParallelWrites() { return false; }

    /** SQLite keeps framework temporal values as sortable fixed-format TEXT. */
    @Override
    protected void bindValue(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Timestamp timestamp) {
            statement.setString(index, DATE_TIME_FORMAT.format(timestamp.toLocalDateTime()));
        } else if (value instanceof java.sql.Date date) {
            statement.setString(index, date.toLocalDate().toString());
        } else if (value instanceof Time time) {
            statement.setString(index, TIME_FORMAT.format(time.toLocalTime()));
        } else {
            super.bindValue(statement, index, value);
        }
    }

    @Override
    public List<String> createTableSql(Class<?> entityClass) {
        return schemaManager.createTableSql(entityClass, isMapUnderscoreToCamelCase());
    }

    /** Creates/validates every scanned schema owner before the Session bean is published. */
    @Override
    public void initializeSchema(Collection<Class<?>> entities) {
        if (schemaMode == SchemaMode.NONE || entities == null || entities.isEmpty()) return;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (Class<?> entity : entities) {
                schemaManager.ensureTable(connection, entity, isMapUnderscoreToCamelCase(), schemaMode,
                        sql -> executeSchemaSql(statement, sql));
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize SQLite schema", error);
        }
    }
}

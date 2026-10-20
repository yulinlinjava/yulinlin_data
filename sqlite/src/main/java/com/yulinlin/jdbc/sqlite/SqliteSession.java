package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.sqlite.coder.SqliteTemporalCoders;
import com.yulinlin.jdbc.session.JdbcSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

/** Shared JDBC execution with SQLite SQL and startup schema initialization. */
public class SqliteSession extends JdbcSession {
    private final SqliteSchemaManager schemaManager = new SqliteSchemaManager();
    private volatile SchemaMode schemaMode = SchemaMode.CREATE;
    private volatile boolean alterColumnType;

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
        alterColumnType = properties.isAlterColumnType();
        setParallelConnections(1);
    }

    @Override public boolean supportsParallelWrites() { return false; }

    /** SQLite keeps framework temporal values as sortable fixed-format TEXT. */
    @Override
    protected void bindValue(PreparedStatement statement, int index, Object value) throws SQLException {
        // 编码器已产生 TEXT；保留原始 SQL/旧参数中的时间对象兼容，与编码器使用同一格式。
        super.bindValue(statement, index, SqliteTemporalCoders.toTextValue(value));
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
                schemaManager.ensureTable(connection, entity, isMapUnderscoreToCamelCase(), schemaMode, alterColumnType,
                        sql -> executeSchemaSql(statement, sql));
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("SQLite 表结构初始化失败；请检查数据库文件、写入权限及下层异常原因", error);
        }
    }
}

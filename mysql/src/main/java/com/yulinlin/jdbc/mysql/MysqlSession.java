package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.session.JdbcSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

/** MySQL execution plus optional startup-only schema creation/validation. */
public class MysqlSession extends JdbcSession {
    @Override
    protected String explainPrefix() { return "EXPLAIN "; }

    private final MysqlSchemaManager schemaManager = new MysqlSchemaManager();
    private volatile SchemaMode schemaMode = SchemaMode.NONE;
    private volatile boolean alterColumnType;

    public MysqlSession(DataSource dataSource) {
        super(dataSource);
        setParseManager(new MysqlParseManager());
    }

    void configure(MysqlProperties properties) {
        if (properties == null || properties.getSchemaMode() == null) {
            throw new IllegalArgumentException("Invalid yulinlin.mysql configuration");
        }
        schemaMode = properties.getSchemaMode();
        alterColumnType = properties.isAlterColumnType();
    }

    @Override
    public List<String> createTableSql(Class<?> entityClass) {
        return schemaManager.createTableSql(entityClass, isMapUnderscoreToCamelCase());
    }

    /** Uses a dedicated startup connection, never a business transaction connection. */
    @Override
    public void initializeSchema(Collection<Class<?>> entities) {
        if (schemaMode == SchemaMode.NONE
                || entities == null || entities.isEmpty()) return;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (Class<?> entity : entities) {
                schemaManager.ensureTable(connection, entity, isMapUnderscoreToCamelCase(), schemaMode, alterColumnType,
                        sql -> executeSchemaSql(statement, sql));
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("MySQL 表结构初始化失败；请检查数据源连接、权限及下层异常原因", error);
        }
    }
}

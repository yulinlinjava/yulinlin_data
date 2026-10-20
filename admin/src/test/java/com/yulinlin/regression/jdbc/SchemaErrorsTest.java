package com.yulinlin.regression.jdbc;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.schema.SchemaColumnAlter;
import com.yulinlin.jdbc.schema.SchemaErrors;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 只检查诊断内容，不连接数据库；测试包与业务实体扫描隔离。 */
class SchemaErrorsTest {
    private static final SchemaErrors.Column NUMBER =
            new SchemaErrors.Column("amount", "INTEGER", "BIGINT", false, 0);
    private static final SchemaErrors.Column TEXT =
            new SchemaErrors.Column("amount", "STRING", "VARCHAR(32)", false, 32);

    @Test
    void disabledAlterExplainsDifferenceAndExactConfiguration() {
        String message = SchemaErrors.column("mysql", "orders", NUMBER, TEXT,
                SchemaMode.CREATE, false).getMessage();
        assertTrue(message.contains("对象：orders.amount"));
        assertTrue(message.contains("字段存储类型不同"));
        assertTrue(message.contains("实体要求：BIGINT"));
        assertTrue(message.contains("数据库现有：VARCHAR(32)"));
        assertTrue(message.contains("自动改列未开启"));
        assertTrue(message.contains("yulinlin.mysql.alter-column-type: true"));
        assertTrue(message.contains("处理方式："));
        assertFalse(message.contains("Column["));
    }

    @Test
    void validateModeIsNotPresentedAsAnAlterFailure() {
        String message = SchemaErrors.column("postgresql", "public.orders", NUMBER, TEXT,
                SchemaMode.VALIDATE, true).getMessage();
        assertTrue(message.contains("仅校验"));
        assertTrue(message.contains("即使 alter-column-type=true 也不会修改结构"));
        assertTrue(message.contains("yulinlin.postgresql.schema-mode: CREATE"));
        assertFalse(message.contains("修改后的结构仍不符合"));
    }

    @Test
    void insufficientLengthReportsCapacityAndEnabledSwitchReportsRemainingMismatch() {
        var longer = new SchemaErrors.Column("amount", "STRING", "VARCHAR(64)", false, 64);
        String message = SchemaErrors.column("h2", "orders", longer, TEXT,
                SchemaMode.CREATE, true).getMessage();
        assertTrue(message.contains("要求至少 64，现有 32"));
        assertTrue(message.contains("自动改列已开启"));
        assertFalse(message.contains("自动改列未开启"));
    }

    @Test
    void primaryDifferencesRequireManualMigration() {
        var expected = new SchemaErrors.Column("id", "STRING", "VARCHAR(32)", true, 32);
        var actual = new SchemaErrors.Column("id", "STRING", "VARCHAR(128)", false, 128);
        String message = SchemaErrors.column("mysql", "users", expected, actual,
                SchemaMode.CREATE, true).getMessage();
        assertTrue(message.contains("主键标记不一致"));
        assertTrue(message.contains("不自动增删主键"));
        assertFalse(message.contains("字符容量不足"));
        assertTrue(SchemaErrors.column("mysql", "users", expected, null,
                SchemaMode.CREATE, false).getMessage().contains("不自动增删主键"));
    }

    @Test
    void missingColumnAndSqliteTypeMismatchHaveDifferentRemedies() {
        assertTrue(SchemaErrors.column("h2", "orders", NUMBER, null, SchemaMode.VALIDATE, false)
                .getMessage().contains("yulinlin.h2.schema-mode: CREATE"));
        String message = SchemaErrors.column("sqlite", "orders", NUMBER, TEXT,
                SchemaMode.CREATE, true).getMessage();
        assertTrue(message.contains("SQLite 不支持自动改列类型"));
        assertTrue(message.contains("手动重建表并迁移数据"));
        assertFalse(message.contains("yulinlin.sqlite.alter-column-type: true"));
    }

    @Test
    void ddlErrorsPreserveDriverIdentityAndFailedSql() {
        SQLException original = new SQLException("Duplicate key name", "42000", 1061);
        var executor = SchemaErrors.executor("mysql", sql -> { throw original; });
        SQLException wrapped = assertThrows(SQLException.class,
                () -> executor.execute("CREATE INDEX idx_orders ON orders(amount)"));
        assertEquals(original.getSQLState(), wrapped.getSQLState());
        assertEquals(original.getErrorCode(), wrapped.getErrorCode());
        assertSame(original, wrapped.getCause());
        assertTrue(wrapped.getMessage().contains("CREATE INDEX idx_orders ON orders(amount)"));
        assertTrue(wrapped.getMessage().contains("不保证整体回滚"));
        assertSame(wrapped, SchemaErrors.failure("mysql", "orders", wrapped).getCause());
    }

    @Test
    void indexAndUnsupportedAttributesDoNotSuggestAutomaticConstraintChanges() {
        String index = SchemaErrors.index("h2", "users.idx_name", List.of("name"), false,
                List.of("name"), true, true).getMessage();
        assertTrue(index.contains("唯一=是"));
        assertTrue(index.contains("不自动删除或替换已有索引"));
        String column = SchemaColumnAlter.unsupported("postgresql", "users", "id",
                "该列被外键引用").getMessage();
        assertTrue(column.contains("数据库类型：postgresql"));
        assertTrue(column.contains("该列被外键引用"));
        assertTrue(column.contains("不会自动删除约束重试"));
    }
}

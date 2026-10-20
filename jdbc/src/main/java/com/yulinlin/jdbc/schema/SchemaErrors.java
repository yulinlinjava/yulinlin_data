package com.yulinlin.jdbc.schema;

import com.yulinlin.data.core.schema.SchemaMode;
import java.sql.SQLException;
import java.util.List;

/** 仅格式化 Schema 诊断，不改变校验规则或执行 DDL。 */
public final class SchemaErrors {
    private SchemaErrors() { }

    public record Column(String name, String type, String display, boolean primary, int length) { }

    public static IllegalStateException problem(String database, String object, String reason, String action) {
        return new IllegalStateException(message(database, object, reason, action));
    }

    public static String message(String database, String object, String reason, String action) {
        return "数据库表结构检查失败\n数据库类型：" + database + "\n对象：" + object
                + "\n原因：" + reason + "\n处理方式：" + action;
    }

    public static IllegalStateException column(String database, String table, Column expected, Column actual,
                                               SchemaMode mode, boolean alterColumnType) {
        String reason;
        String action;
        if (actual == null) {
            reason = "数据库缺少实体声明的列";
            action = expected.primary() ? primaryAction()
                    : creationAction(database, mode) + "；如果字段曾改名，请手动迁移旧列数据，框架不会识别重命名";
        } else if (expected.primary() != actual.primary()) {
            reason = "主键标记不一致";
            action = primaryAction();
        } else {
            reason = !expected.type().equals(actual.type()) ? "字段存储类型不同"
                    : "字段字符容量不足（要求至少 " + expected.length() + "，现有 " + actual.length() + "）";
            action = alterationAction(database, mode, alterColumnType);
        }
        String details = "\n实体要求：" + expected.display() + (expected.primary() ? "，主键" : "，非主键")
                + "\n数据库现有：" + (actual == null ? "列不存在"
                : actual.display() + (actual.primary() ? "，主键" : "，非主键"));
        return problem(database, table + "." + expected.name(), reason + details, action);
    }

    public static String alterationAction(String database, SchemaMode mode, boolean enabled) {
        if ("sqlite".equals(database)) {
            return (mode == SchemaMode.VALIDATE ? "当前 schema-mode=VALIDATE，仅校验，不执行 DDL；" : "")
                    + "SQLite 不支持自动改列类型，请备份后手动重建表并迁移数据；alter-column-type 开关不能解决此限制";
        }
        String prefix = "yulinlin." + database + ".";
        if (mode == SchemaMode.VALIDATE)
            return "当前 schema-mode=VALIDATE，仅校验；即使 alter-column-type=true 也不会修改结构。"
                    + "请手动迁移；或确认数据可转换并备份后，设置 " + prefix + "schema-mode: CREATE 和 "
                    + prefix + "alter-column-type: true。修改类型可能锁表或改变数据精度";
        if (!enabled)
            return "自动改列未开启。请手动迁移；或确认数据可转换并备份后，设置 "
                    + prefix + "alter-column-type: true，并确保 " + prefix
                    + "schema-mode: CREATE。修改类型可能锁表或改变数据精度";
        return "自动改列已开启，但修改后的结构仍不符合实体要求。请核对上方 DDL、实际连接的数据库、"
                + "实体字段映射及数据库原始错误并手动迁移；此前 DDL 可能已生效，不要直接重复操作";
    }

    public static String primaryAction() {
        return "请核对实体 @JoinMeta(primaryKey=true) 与数据库主键定义，人工检查空值、重复值及外键后迁移；"
                + "框架不自动增删主键，开启 alter-column-type 也不会处理主键约束差异";
    }

    public static String creationAction(String database, SchemaMode mode) {
        return mode == SchemaMode.VALIDATE
                ? "当前 schema-mode=VALIDATE，仅校验。请手动创建缺失结构；或确认由框架管理后设置 yulinlin."
                + database + ".schema-mode: CREATE，alter-column-type 不能替代 CREATE"
                : "请核对实际数据库、表结构权限和前面的 DDL 日志，手动补齐缺失结构后重启";
    }

    public static IllegalStateException missing(String database, String object, SchemaMode mode) {
        return problem(database, object, "数据库中未找到要求的表、列或索引", creationAction(database, mode));
    }

    public static IllegalStateException primary(String database, String table) {
        return problem(database, table, "实体与数据库的主键字段集合不一致", primaryAction());
    }

    public static IllegalStateException readOnly(String database, String object) {
        return problem(database, object, "结构缺失，但初始化连接为只读",
                "请使用有建表权限的可写连接在启动阶段初始化，或手动创建结构；不要在只读事务中执行 DDL");
    }

    public static IllegalStateException index(String database, String object,
                                              List<String> expected, boolean expectedUnique,
                                              List<String> actual, boolean actualUnique, boolean plainAscending) {
        return problem(database, object, "索引字段、顺序、唯一性或索引形式不一致"
                        + "\n实体要求：字段=" + expected + "，唯一=" + (expectedUnique ? "是" : "否")
                        + "\n数据库现有：字段=" + actual + "，唯一=" + (actualUnique ? "是" : "否")
                        + "，普通升序索引=" + (plainAscending ? "是" : "否"),
                "请核对 @JoinIndex 与实际索引，检查重复数据及业务依赖后手动迁移；"
                        + "框架不自动删除或替换已有索引，alter-column-type 不处理索引定义冲突");
    }

    public static IllegalStateException failure(String database, String object, SQLException error) {
        return new IllegalStateException(message(database, object, "读取元数据或执行表结构 SQL 失败\n数据库原始原因："
                + error.getMessage(), "请核对实际连接、数据库权限和失败 SQL；修复后再启动。"
                + "此前 DDL 可能已生效，多条 DDL 不保证整体回滚"), error);
    }

    public static SchemaSqlExecutor executor(String database, SchemaSqlExecutor delegate) {
        return sql -> {
            try {
                delegate.execute(sql);
            } catch (SQLException error) {
                throw new SQLException("数据库表结构 SQL 执行失败\n数据库类型：" + database
                        + "\n失败 SQL：\n" + sql + "\n数据库原始原因：" + error.getMessage()
                        + "\n处理方式：请检查数据格式、值域、唯一性、默认值、约束及权限后手动处理；"
                        + "此前 DDL 可能已生效，不保证整体回滚",
                        error.getSQLState(), error.getErrorCode(), error);
            }
        };
    }

    public static String textType(String ddl, int length) {
        if (length > 0 && (ddl.startsWith("VARCHAR") || ddl.startsWith("CHARACTER VARYING")))
            return "VARCHAR(" + length + ")";
        return ddl;
    }
}

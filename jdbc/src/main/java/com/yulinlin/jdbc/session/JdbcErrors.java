package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.exception.DuplicateDataException;
import com.yulinlin.data.core.exception.OrmException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import java.sql.SQLException;

/** 复用 JDBC 错误分类，对外仅暴露 ORM 异常。 */
final class JdbcErrors {
    private static final SQLExceptionSubclassTranslator TRANSLATOR = new SQLExceptionSubclassTranslator();
    private JdbcErrors() { }
    static RuntimeException translate(Throwable error) {
        if (error instanceof SQLException sql) {
            var translated = TRANSLATOR.translate("ORM JDBC 执行失败", null, sql);
            return translated instanceof DuplicateKeyException
                    ? new DuplicateDataException("数据重复：主键或唯一约束冲突", sql)
                    : new OrmException("JDBC 执行失败：" + sql.getMessage(), sql);
        }
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new OrmException("JDBC 执行失败", error);
    }
}

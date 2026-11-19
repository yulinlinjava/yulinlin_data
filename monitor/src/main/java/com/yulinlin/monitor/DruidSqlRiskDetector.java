package com.yulinlin.monitor;

import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.wall.spi.MySqlWallProvider;
import com.alibaba.druid.wall.spi.MySqlWallVisitor;
import com.alibaba.druid.wall.WallVisitor;
import com.alibaba.druid.wall.Violation;
import com.alibaba.druid.wall.violation.ErrorCode;
import com.alibaba.druid.wall.violation.IllegalSQLObjectViolation;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.dialect.mysql.ast.expr.MySqlOutFileExpr;
import com.alibaba.druid.wall.violation.SyntaxErrorViolation;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/** Standalone Wall checking; does not install a Druid connection pool or rewrite SQL. */
public final class DruidSqlRiskDetector implements SqlRiskDetector {
    private static final Set<String> DENIED_FUNCTIONS = Set.of("sleep", "benchmark", "load_file");
    private final MySqlWallProvider provider;

    public DruidSqlRiskDetector() {
        WallConfig config = new WallConfig();
        config.setMultiStatementAllow(false);
        config.setCommentAllow(true);
        config.setStrictSyntaxCheck(true);
        config.setDoPrivilegedAllow(false);
        config.setSelectIntoOutfileAllow(false);
        config.setDropTableAllow(false);
        config.setTruncateAllow(false);
        // Single business CREATE/ALTER statements are checked, not rejected solely for being DDL.
        config.setCreateTableAllow(true);
        config.setAlterTableAllow(true);
        config.getDenyFunctions().addAll(DENIED_FUNCTIONS);
        config.setCompleteInsertValuesCheck(true);
        provider = new MySqlWallProvider(config) {
            @Override public WallVisitor createWallVisitor() {
                return new MySqlWallVisitor(this) {
                    private final ArrayList<Violation> strictViolations = new ArrayList<>();

                    @Override public void postVisit(SQLObject node) {
                        super.postVisit(node);
                        // Druid may clear ordinary violations when it encounters a system table.
                        // Apply our explicit capability policy after the statement has been visited.
                        if (node.getParent() == null) {
                            strictViolations.forEach(this::addViolation);
                            strictViolations.clear();
                        }
                    }

                    @Override public boolean visit(SQLMethodInvokeExpr expression) {
                        boolean visitChildren = super.visit(expression);
                        // Wall normally exempts top-level SELECT functions without FROM.
                        // These three capabilities are deliberately denied in every position.
                        String name = expression.getMethodName().replace("`", "").toLowerCase(Locale.ROOT);
                        if (DENIED_FUNCTIONS.contains(name)) strictViolations.add(new IllegalSQLObjectViolation(
                                ErrorCode.FUNCTION_DENY, "deny function : " + name, ""));
                        return visitChildren;
                    }

                    @Override public boolean visit(MySqlOutFileExpr expression) {
                        super.visit(expression);
                        strictViolations.add(new IllegalSQLObjectViolation(ErrorCode.SELECT_INTO_NOT_ALLOW,
                                "file export not allow", ""));
                        return true;
                    }
                };
            }
        };
    }

    @Override
    public SqlRiskResult check(String sql) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL 模板不能为空");
        // Druid 1.2.28 can merge a single statement and a commented multi-statement
        // into the same cache key. Check statement boundaries before cache lookup.
        // Parsing (rather than splitting on ';') preserves literals and comments.
        if (sql.indexOf(';') >= 0) {
            final int statementCount;
            try {
                statementCount = SQLUtils.parseStatements(sql, DbType.mysql).size();
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Druid 无法解析 MySQL SQL 模板", error);
            }
            if (statementCount > 1) return SqlRiskResult.reject("multi-statement not allow");
        }
        var result = provider.check(sql);
        if (result.isSyntaxError() || result.getViolations().stream().anyMatch(SyntaxErrorViolation.class::isInstance))
            throw new IllegalArgumentException("Druid 无法解析 MySQL SQL 模板");
        var reasons = result.getViolations().stream()
                .map(violation -> "Druid[" + violation.getErrorCode() + "]: " + violation.getMessage())
                .distinct().toList();
        return new SqlRiskResult(reasons.isEmpty(), reasons);
    }
}

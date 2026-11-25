package com.yulinlin.mcp.data;

import java.util.*;
import java.util.regex.Pattern;

/**
 * SQL 语句检查。只是兜底，真正的保证是只读数据库账号、只读连接和强制回滚。
 * 检查前会去掉注释，并把字符串和带引号的标识符替换成空白，避免被其中的关键字误导。
 */
public final class SqlGuard {
    private SqlGuard() { }

    public enum Kind { QUERY, WRITE }

    private static final Set<String> QUERY_START = Set.of("select", "with", "explain", "show", "describe", "desc", "values", "table");
    private static final Set<String> WRITE_START = Set.of("insert", "update", "delete", "merge", "replace", "upsert");
    /** WITH 语句内部的写操作；按 DML 的完整形状匹配，避免把 replace()、列名 update_time 等误判为写入。 */
    private static final Pattern WRITE_WORD = Pattern.compile(
            "\\binsert\\s+into\\b|\\bdelete\\s+from\\b|\\bupdate\\s+\\S+(\\s+\\S+)?\\s+set\\b|\\bmerge\\s+into\\b");
    /**
     * 允许的语句内部仍可能夹带的危险写法：导出/读取服务器文件、跨库连接、故意拖慢。
     * DDL、事务控制、SET、USE 等不是查询或写入开头，已经被首个关键字检查拒绝。
     */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\binto\\s+(outfile|dumpfile)\\b|\\bload_file\\s*\\(|\\bpg_read_(binary_)?file\\s*\\(|\\blo_(import|export)\\s*\\("
            + "|\\bdblink\\w*\\s*\\(|\\bpg_sleep\\w*\\s*\\(|\\bsleep\\s*\\(|\\bbenchmark\\s*\\(");
    private static final Pattern READ_ONLY_FORBIDDEN = Pattern.compile(
            "\\bfor\\s+(update|share|no\\s+key\\s+update|key\\s+share)\\b|\\block\\s+in\\s+share\\s+mode\\b|\\bselect\\b[^;]*\\binto\\b|\\bexplain\\s+analy[sz]e\\b");

    /**
     * 检查语句并返回类型。
     *
     * @param inWriteTransaction 是否在可写事务中（允许 SELECT ... FOR UPDATE）
     */
    public static Kind check(String sql, boolean inWriteTransaction) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL 不能为空");
        if (sql.length() > 100_000) throw new IllegalArgumentException("SQL 最长 100000 字符");
        if (sql.contains("${"))
            throw new IllegalArgumentException("不支持 ${...} 文本替换，值请用 #{名称} 占位并通过 params 传入");
        String masked = mask(sql).strip();
        if (masked.endsWith(";")) masked = masked.substring(0, masked.length() - 1).strip();
        if (masked.isEmpty()) throw new IllegalArgumentException("SQL 不能为空");
        if (masked.indexOf(';') >= 0) throw new IllegalArgumentException("一次只能执行一条语句，不能用分号拼接多条");
        String lower = masked.toLowerCase(Locale.ROOT);
        String first = lower.replaceFirst("^[\\s(]+", "").split("[\\s(]+", 2)[0];
        if (FORBIDDEN.matcher(lower).find())
            throw new IllegalArgumentException("不允许读写服务器文件、跨库连接或 sleep/benchmark 类函数");
        if (WRITE_START.contains(first)) {
            requireWhere(lower, first);
            return Kind.WRITE;
        }
        if (!QUERY_START.contains(first))
            throw new IllegalArgumentException("不支持的语句类型: " + first + "。只支持查询（SELECT/WITH/EXPLAIN/SHOW）和 INSERT/UPDATE/DELETE/MERGE，DDL、事务控制、会话设置一律拒绝");
        if (first.equals("with") && WRITE_WORD.matcher(lower).find()) {
            // WITH ... DELETE/UPDATE：按写语句处理
            return Kind.WRITE;
        }
        if (!inWriteTransaction && READ_ONLY_FORBIDDEN.matcher(lower).find())
            throw new IllegalArgumentException("只读查询不能加锁、不能 SELECT INTO、不能 EXPLAIN ANALYZE");
        return Kind.QUERY;
    }

    private static void requireWhere(String lower, String first) {
        if ((first.equals("update") || first.equals("delete")) && !Pattern.compile("\\bwhere\\b").matcher(lower).find())
            throw new IllegalArgumentException(first.toUpperCase(Locale.ROOT) + " 必须带 WHERE 条件；确实要全表操作请写 WHERE 1=1");
    }

    /** 去掉注释，把字符串、带引号的标识符替换成空格，保留长度以外的结构。 */
    static String mask(String sql) {
        var out = new StringBuilder(sql.length());
        int i = 0, n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') i++;
                out.append(' ');
            } else if (c == '#' && (i + 1 >= n || sql.charAt(i + 1) != '{')) {
                // MySQL 行注释；#{name} 是参数占位符
                while (i < n && sql.charAt(i) != '\n') i++;
                out.append(' ');
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("SQL 注释没有结束");
                i = end + 2;
                out.append(' ');
            } else if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (true) {
                    if (j >= n) throw new IllegalArgumentException("SQL 中的引号没有闭合");
                    char d = sql.charAt(j);
                    if (d == '\\' && c == '\'' && j + 1 < n) { j += 2; continue; }
                    if (d == c) {
                        if (j + 1 < n && sql.charAt(j + 1) == c) { j += 2; continue; }
                        break;
                    }
                    j++;
                }
                out.append(c == '\'' ? " '' " : " q ");
                i = j + 1;
            } else if (c == '$' && i + 1 < n && sql.charAt(i + 1) == '$') {
                // PostgreSQL $$ 字符串
                int end = sql.indexOf("$$", i + 2);
                if (end < 0) throw new IllegalArgumentException("SQL 中的 $$ 没有闭合");
                out.append(" '' ");
                i = end + 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}

package com.yulinlin.jdbc.log;

import java.util.regex.Pattern;

/** 仅供日志阅读的保守词法美化器；结果不可回写 SqlNode，也不保证可直接执行。 */
final class SqlLogFormatter {
    private static final Pattern SIMPLE_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$]*");

    private SqlLogFormatter() { }

    static String readable(String sql, char identifierQuote) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean space = false;
        char previous = 0;
        for (int i = 0; i < sql.length();) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                space = true;
                i++;
                continue;
            }
            int end = i + 1;
            String token;
            boolean protectedToken = false;
            if (c == '\'' || c == '"' || c == '`') {
                end = quotedEnd(sql, i, c);
                token = sql.substring(i, end);
                // 保留带空格、点号、转义符的复杂名称及未闭合引用。
                // 前缀 U&"..." 等方言表达式也不去引用。
                boolean prefixed = i > 0 && (isNamePart(sql.charAt(i - 1)) || sql.charAt(i - 1) == '&');
                if (c == identifierQuote && !prefixed && token.length() >= 2
                        && token.charAt(token.length() - 1) == c
                        && SIMPLE_NAME.matcher(token.substring(1, token.length() - 1)).matches()) {
                    token = token.substring(1, token.length() - 1);
                } else {
                    protectedToken = true;
                }
            } else if (sql.startsWith("--", i) || c == '#' && identifierQuote == '`') {
                while (end < sql.length() && sql.charAt(end - 1) != '\n' && sql.charAt(end - 1) != '\r') end++;
                token = sql.substring(i, end);
                protectedToken = true;
            } else if (sql.startsWith("/*", i)) {
                end = commentEnd(sql, i);
                token = sql.substring(i, end);
                protectedToken = true;
            } else if (c == '$' && (i == 0 || !isNamePart(sql.charAt(i - 1))) && dollarDelimiter(sql, i) != null) {
                String delimiter = dollarDelimiter(sql, i);
                int close = sql.indexOf(delimiter, i + delimiter.length());
                end = close < 0 ? sql.length() : close + delimiter.length();
                token = sql.substring(i, end);
                protectedToken = true;
            } else {
                token = String.valueOf(c);
            }

            boolean punctuation = !protectedToken && token.length() == 1;
            boolean tightBefore = punctuation && (c == ',' || c == ')' || c == '.');
            if (space && !result.isEmpty() && !tightBefore && previous != '(' && previous != '.') result.append(' ');
            if (protectedToken) appendProtected(result, token);
            else result.append(token);
            // 引用片段内部的括号/逗号不参加外部空格处理。
            previous = protectedToken ? 0 : token.charAt(token.length() - 1);
            space = punctuation && c == ',';
            i = end;
        }
        return result.toString();
    }

    private static int quotedEnd(String sql, int start, char quote) {
        for (int i = start + 1; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\\' && i + 1 < sql.length()) i++;
            else if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) i++;
                else return i + 1;
            }
        }
        return sql.length();
    }

    private static int commentEnd(String sql, int start) {
        int depth = 1;
        for (int i = start + 2; i < sql.length();) {
            if (sql.startsWith("/*", i)) { depth++; i += 2; }
            else if (sql.startsWith("*/", i)) {
                i += 2;
                if (--depth == 0) return i;
            } else i++;
        }
        return sql.length();
    }

    private static String dollarDelimiter(String sql, int start) {
        int i = start + 1;
        if (i < sql.length() && sql.charAt(i) == '$') return "$$";
        if (i == sql.length() || !(Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_')) return null;
        while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) i++;
        return i < sql.length() && sql.charAt(i) == '$' ? sql.substring(start, i + 1) : null;
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static void appendProtected(StringBuilder result, String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            switch (c) {
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') {
                        String hex = Integer.toHexString(c);
                        result.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
                    } else result.append(c);
                }
            }
        }
    }
}

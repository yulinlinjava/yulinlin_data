package com.yulinlin.sync;

import java.util.List;
import java.util.regex.Pattern;

/** 类名按包段匹配：* 不跨点，** 匹配零个或多个完整包段。 */
public final class EntityPattern {
    private final List<Pattern> patterns;
    private final ClassValue<Boolean> matches = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return patterns.stream().anyMatch(pattern -> pattern.matcher(type.getName()).matches());
        }
    };

    public EntityPattern(List<String> expressions) {
        if (expressions == null || expressions.isEmpty())
            throw new IllegalArgumentException("同步 entities 不能为空，请明确指定实体类或通配符");
        patterns = expressions.stream().map(EntityPattern::compile).toList();
    }

    public boolean matches(Class<?> type) {
        return type != null && type != Object.class && matches.get(type);
    }

    private static Pattern compile(String expression) {
        if (expression == null || expression.isBlank())
            throw new IllegalArgumentException("同步 entities 表达式不能为空");
        String[] segments = expression.trim().split("\\.", -1);
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < segments.length; i++) {
            String part = segments[i];
            if (part.equals("**")) {
                regex.append(i == segments.length - 1 ? "(?:[^.]+\\.)*[^.]+" : "(?:[^.]+\\.)*");
                continue;
            }
            if (!part.matches("[\\p{L}\\p{N}_$*]+") || part.contains("**"))
                throw new IllegalArgumentException("同步 entities 表达式不合法：" + expression
                        + "；* 不跨包，** 必须独占一个包段");
            for (char c : part.toCharArray()) {
                regex.append(c == '*' ? "[^.]*" : Pattern.quote(String.valueOf(c)));
            }
            if (i < segments.length - 1) regex.append("\\.");
        }
        return Pattern.compile(regex.append("$").toString());
    }
}

package com.yulinlin.mcp.auth;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 凭证访问范围。保存和返回的都是下面的规范写法，<b>不带 {@code tool:} 前缀</b>。
 * <ul>
 *   <li>{@code *}：继承所属账号的全部工具权限</li>
 *   <li>{@code echo}：精确工具名，与 {@code GET /api/mcp/tools} 的 {@code name} 一致</li>
 *   <li>{@code netdisk.*}：工具名前缀通配</li>
 *   <li>{@code @readonly}：所有只读工具</li>
 *   <li>{@code resource:...}、{@code prompt:...}：预留，当前不参与判断</li>
 * </ul>
 * 输入时也接受 {@code tool:echo} 这种带前缀的写法，规范化时去掉前缀。
 * 范围只做收窄，最终仍由 {@link McpAccessPolicy#canInvoke} 判断账号本身是否有权限。
 */
public final class McpScope {
    public static final String ALL = "*";
    /** 只在输入时接受的前缀，保存时去掉。 */
    public static final String TOOL = "tool:";
    public static final String READ_ONLY = "@readonly";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_.-]{1,127}\\*");
    private static final Pattern RESERVED = Pattern.compile("(resource|prompt):[\\x21-\\x7e]{1,256}");

    private McpScope() { }

    /** null 视为 {@code *}；返回去重、去前缀后的不可变列表。 */
    public static List<String> normalize(Collection<String> entries) {
        if (entries == null) return List.of(ALL);
        if (entries.size() > 256) throw new IllegalArgumentException("范围最多 256 项");
        var result = new LinkedHashSet<String>();
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException("范围项不能为空");
            String entry = raw.strip();
            // 兼容带前缀的输入。tool:* 去掉前缀后就是 *，顺带被下一行收掉
            if (entry.startsWith(TOOL)) entry = entry.substring(TOOL.length());
            if (entry.equals(ALL)) { result.add(ALL); continue; }
            if (!reserved(entry) && !tool(entry)) throw new IllegalArgumentException("无效的范围: " + raw);
            result.add(entry);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("范围不能为空");
        if (result.contains(ALL) && result.size() > 1) throw new IllegalArgumentException("* 不能和其他范围混用");
        return List.copyOf(result);
    }

    /** 工具项：精确名、前缀通配或 @readonly。三者都不含冒号，所以和预留项不会混 */
    private static boolean tool(String entry) {
        return entry.equals(READ_ONLY) || NAME.matcher(entry).matches() || PREFIX.matcher(entry).matches();
    }

    private static boolean reserved(String entry) {
        return RESERVED.matcher(entry).matches();
    }

    public static boolean allowsTool(List<String> scope, String tool, boolean readOnly) {
        if (scope == null || tool == null) return false;
        for (String entry : scope) {
            if (entry.equals(ALL)) return true;
            if (reserved(entry)) continue;
            if (entry.equals(tool)) return true;
            if (entry.equals(READ_ONLY) && readOnly) return true;
            if (entry.endsWith("*") && tool.startsWith(entry.substring(0, entry.length() - 1))) return true;
        }
        return false;
    }

    /** 范围里写死的精确工具名，用于签发时提前校验工具是否存在、账号是否有权限。 */
    public static List<String> exactTools(List<String> scope) {
        if (scope == null) return List.of();
        return scope.stream()
                .filter(e -> !e.equals(ALL) && !e.equals(READ_ONLY) && !e.endsWith("*") && !reserved(e))
                .toList();
    }
}

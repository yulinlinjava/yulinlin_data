package com.yulinlin.mcp.token;

/**
 * 令牌查询条件。
 *
 * @param ownerId 为 null 表示全部（仅管理员）
 * @param status  为 null 表示全部；ACTIVE / EXPIRED 需要结合 now 判断
 * @param keyword 匹配名称（模糊）或 keyId（精确）
 * @param now     当前时间，用于 ACTIVE / EXPIRED 过滤
 */
public record McpTokenQuery(String ownerId, McpTokenStatus status, String keyword, int page, int size, long now) {
    public McpTokenQuery {
        if (page < 1 || page > 10000) throw new IllegalArgumentException("page 范围 1–10000");
        if (size < 1 || size > 200) throw new IllegalArgumentException("size 范围 1–200");
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (keyword != null && keyword.length() > 60) throw new IllegalArgumentException("keyword 最多 60 字符");
    }
}

package com.yulinlin.mcp.token;

import java.util.List;

/**
 * 令牌存储 SPI，默认实现 {@link OrmMcpTokenStore}。实现必须按 applicationId 隔离。
 */
public interface McpTokenStore {
    default void initialize() { }

    McpToken findById(String id);

    /** keyId 唯一索引查找，认证热路径。 */
    McpToken findByKeyId(String keyId);

    List<McpToken> list(McpTokenQuery query);

    long count(McpTokenQuery query);

    /** 某账号下未撤销的令牌数量，用于数量上限。 */
    long countUnrevoked(String ownerId);

    /** keyId 冲突时抛异常。 */
    void insert(McpToken token);

    /**
     * 乐观锁更新：仅当库中 version 等于 expectedVersion 时写入 token 的全部可变字段（含新 version）。
     * 不得覆盖 lastUsedAt / lastUsedIp。
     *
     * @return 是否更新成功
     */
    boolean update(McpToken token, long expectedVersion);

    /** 只更新使用信息，单调递增且最多每分钟一次；不改 version。 */
    void markUsed(String id, long usedAt, String ip);

    void delete(String id);
}

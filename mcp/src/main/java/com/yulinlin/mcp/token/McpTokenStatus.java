package com.yulinlin.mcp.token;

/** 令牌状态。库里只存 ACTIVE / DISABLED / REVOKED；EXPIRED 由过期时间计算得出，不入库。 */
public enum McpTokenStatus {
    /** 正常可用 */
    ACTIVE,
    /** 暂停，可重新启用 */
    DISABLED,
    /** 已撤销，不可恢复 */
    REVOKED,
    /** 已过期（计算状态），续期后恢复为 ACTIVE */
    EXPIRED
}

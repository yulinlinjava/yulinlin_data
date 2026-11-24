package com.yulinlin.mcp.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 认证方式 SPI。默认提供静态令牌 {@code TokenAuthenticator}；OAuth、SSO、网关签名等可另行实现并注册为 Bean，
 * 多个实现按 {@code @Order} 依次尝试。
 */
public interface McpAuthenticator {
    /** 稳定的类型标识，写入 {@link McpPrincipal#type()}，用于 {@link #refresh} 找回对应认证器。 */
    String type();

    /**
     * 请求不包含本认证器能识别的凭证时返回 null，交给下一个认证器；
     * 能识别但凭证无效时抛出 {@code McpException(401)}。
     */
    McpPrincipal authenticate(HttpServletRequest request);

    /**
     * 后台任务等延迟执行前重新确认凭证仍然有效，返回最新身份；已失效返回 null。
     * 默认认为不会变化。
     */
    default McpPrincipal refresh(McpPrincipal principal) { return principal; }
}

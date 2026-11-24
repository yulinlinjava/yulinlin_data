package com.yulinlin.mcp.auth;

import java.util.*;

/**
 * 一次 MCP 请求的调用方身份，由 {@link McpAuthenticator} 产生。
 *
 * @param type         产生它的认证器类型，例如 {@code token}
 * @param credentialId 凭证标识（令牌 id、OAuth client id 等），任务归属和审计使用
 * @param name         凭证显示名称
 * @param accountId    所属账号，交给 {@link McpIdentityProvider#load} 加载当前权限
 * @param scope        访问范围，见 {@link McpScope}
 * @param attributes   业务属性，框架只透传不解释，例如 {@code netdisk.root=/项目A}
 */
public record McpPrincipal(String type, String credentialId, String name, String accountId,
                           List<String> scope, Map<String, String> attributes) {
    public McpPrincipal {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(credentialId, "credentialId");
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId 不能为空");
        name = name == null ? credentialId : name;
        scope = McpScope.normalize(scope);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public String attribute(String key) { return attributes.get(key); }
}

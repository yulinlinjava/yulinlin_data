package com.yulinlin.security.auth;

/**
 * 仅供已完成可信外部验证的服务端代码使用。用户 ID 不是凭证；
 * 禁止直接绑定到允许客户端任意提交 ID 的公开登录接口。
 */
public record UserIdLogin(String userId, String deviceType) implements LoginRequest {
    public UserIdLogin(String userId) { this(userId, "default"); }
    public UserIdLogin { if (deviceType == null) deviceType = "default"; }
    @Override public String toString() { return "UserIdLogin[服务端可信登录]"; }
}

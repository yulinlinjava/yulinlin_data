package com.yulinlin.security.auth;

/** 使用业务分发的 API Key 换取登录凭证，不表示每次请求直接携带 API Key 认证。 */
public record ApiKeyLogin(String apiKey, String deviceType) implements LoginRequest {
    public ApiKeyLogin(String apiKey) {
        this(apiKey, "default");
    }

    public ApiKeyLogin {
        if (deviceType == null) deviceType = "default";
    }

    @Override public String toString() { return "ApiKeyLogin[凭证已隐藏]"; }
}

package com.yulinlin.security.spring;

import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.LoginRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;
import java.util.Objects;

/** 提交到 Spring AuthenticationManager 的统一未认证请求；不能由调用方直接标为已认证。 */
public final class LoginRequestAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID = 1L;
    private transient LoginRequest request;
    private final String authType;

    public LoginRequestAuthenticationToken(LoginRequest request, String authType) {
        super(List.of());
        this.request = Objects.requireNonNull(request, "登录请求不能为空");
        this.authType = LoginAuthenticatorRegistry.requireName(authType);
    }

    public LoginRequest getLoginRequest() { return request; }
    public String getAuthType() { return authType; }
    @Override public Object getPrincipal() { return ""; }
    @Override public Object getCredentials() { return request; }

    @Override public void setAuthenticated(boolean authenticated) {
        if (authenticated) throw new IllegalArgumentException("登录请求不能直接标记为已认证，请由 AuthenticationManager 验证");
        super.setAuthenticated(false);
    }

    @Override public void eraseCredentials() {
        request = null;
        super.eraseCredentials();
    }
}

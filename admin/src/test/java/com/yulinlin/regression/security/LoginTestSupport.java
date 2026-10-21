package com.yulinlin.regression.security;

import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginAuthenticator;
import com.yulinlin.security.auth.LoginRequest;

import java.util.function.Function;

import static org.mockito.Mockito.*;

/** 测试认证器工厂；不注册业务 Bean，不参与 admin 的生产扫描。 */
final class LoginTestSupport {
    private LoginTestSupport() { }

    static <T extends LoginRequest> LoginAuthenticator<T> authenticator(
            Class<T> type, String name, Function<T, IUserDetails> verifier) {
        return new LoginAuthenticator<>() {
            @Override public String name() { return name; }
            @Override public Class<T> requestType() { return type; }
            @Override public IUserDetails authenticate(T request) { return verifier.apply(request); }
        };
    }

    @SuppressWarnings("unchecked")
    static <T extends LoginRequest> LoginAuthenticator<T> mockAuthenticator(Class<T> type, String name) {
        LoginAuthenticator<T> result = mock(LoginAuthenticator.class);
        when(result.name()).thenReturn(name);
        when(result.requestType()).thenReturn(type);
        return result;
    }
}

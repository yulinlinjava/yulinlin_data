package com.yulinlin.regression.security;

import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginAuthenticator;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.UserIdLogin;
import com.yulinlin.security.auth.ApiKeyLogin;
import com.yulinlin.security.auth.PasswordLogin;
import com.yulinlin.security.auth.LoginRequest;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.jwt.JwtProperties;
import com.yulinlin.security.jwt.JwtSecurityManager;
import com.yulinlin.security.jwt.JwtTokenCodec;
import com.yulinlin.security.satoken.SaTokenSecurityManager;
import com.yulinlin.security.spring.SpringSecurityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.yulinlin.regression.security.LoginTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 统一登录入口与认证边界，不连接外部服务。 */
@ResourceLock("SecurityUtil")
class UsernameLoginEntryTest {
    @AfterEach void cleanup() { SecurityContext.clear(); }

    @Test void onlyUnifiedRequestEntryRemainsOnTheProtocolFacadeOrBuiltInManagers() {
        for (Class<?> type : new Class<?>[]{ISecurityManager.class, SecurityUtil.class,
                JwtSecurityManager.class, SaTokenSecurityManager.class, SpringSecurityManager.class}) {
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("login", String.class));
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("login", String.class, String.class));
            assertDoesNotThrow(() -> type.getMethod("login", LoginRequest.class));
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("loginByUsername", String.class, String.class));
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("loginByUsername", String.class, String.class, String.class));
        }
    }

    @Test void jwtUsesTheVerifiedUserIdNotTheUsernameAndDoesNotReloadTheAuthenticationResult() {
        UserStore users = mock(UserStore.class);
        LoginAuthenticator<PasswordLogin> authentication = mockAuthenticator(PasswordLogin.class, "password");
        IUserDetails user = mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("db-user-42");
        when(authentication.authenticate(new PasswordLogin("alice", " password "))).thenReturn(user);
        JwtSecurityManager manager = new JwtSecurityManager(users, properties(), registry(users, authentication));
        var result = manager.login(new PasswordLogin("alice", " password "));
        assertSame(user, result.user());
        assertEquals("db-user-42", SecurityContext.getUserId());
        verify(authentication).authenticate(new PasswordLogin("alice", " password "));
        verifyNoInteractions(users); // 认证返回的安全 DTO 可直接使用；后续请求再交给 UserStore
        var codec = new com.yulinlin.security.jwt.DefaultJwtTokenCodec(properties());
        assertEquals("db-user-42", codec.parse(result.token()).userId());
        assertEquals("default", codec.parse(result.token()).deviceType());
        assertEquals("password", codec.parse(result.token()).authType());
        assertEquals("password", result.authType());
    }

    @Test void apiKeyLoginUsesVerifiedIdAndRestoresUserWithoutRecheckingTheKey() {
        UserStore users = mock(UserStore.class);
        LoginAuthenticator<PasswordLogin> passwords = mockAuthenticator(PasswordLogin.class, "password");
        LoginAuthenticator<ApiKeyLogin> keys = mockAuthenticator(ApiKeyLogin.class, "api-key");
        IUserDetails user = mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("service-42");
        when(keys.authenticate(new ApiKeyLogin("opaque-key", "server"))).thenReturn(user);
        when(users.load("service-42")).thenReturn(user);
        var manager = new JwtSecurityManager(users, properties(), registry(users, passwords, keys));

        var result = manager.login(new ApiKeyLogin("opaque-key", "server"));
        assertSame(user, result.user());
        var info = new com.yulinlin.security.jwt.DefaultJwtTokenCodec(properties()).parse(result.token());
        assertEquals("service-42", info.userId());
        assertEquals("server", info.deviceType());
        assertEquals("api-key", info.authType());
        assertEquals("api-key", result.authType());
        verify(passwords, never()).authenticate(any(PasswordLogin.class));
        verifyNoInteractions(users);
        SecurityContext.clear();
        assertSame(user, manager.resolveSession(result.token()).user());
        assertEquals("api-key", manager.resolveSession(result.token()).authType());
        verify(users, times(2)).load("service-42");
        verify(keys, times(1)).authenticate(new ApiKeyLogin("opaque-key", "server"));
        manager.logout();
        assertNotNull(manager.resolveSession(result.token())); // JWT 保持无状态语义
    }

    @Test void apiKeyRejectionNeverFallsBackOrGeneratesATokenAndRemovesOldContext() {
        UserStore users = mock(UserStore.class);
        JwtTokenCodec codec = mock(JwtTokenCodec.class);
        LoginAuthenticator<PasswordLogin> passwords = mockAuthenticator(PasswordLogin.class, "password");
        LoginAuthenticator<ApiKeyLogin> keys = mockAuthenticator(ApiKeyLogin.class, "api-key");
        var missing = new JwtSecurityManager(users, codec, properties(), registry(users, passwords));
        assertThrows(IllegalStateException.class, () -> missing.login(new ApiKeyLogin("key")));
        var manager = new JwtSecurityManager(users, codec, properties(), registry(users, passwords, keys));
        IUserDetails old = mock(IUserDetails.class);
        when(old.getUserId()).thenReturn("old-user");
        SecurityContext.set(new com.yulinlin.security.auth.LoginSession("old-token", null, old, "password"));
        assertThrows(LoginException.class, () -> manager.login(new ApiKeyLogin("bad-key")));
        assertNull(SecurityContext.getUser());
        for (String value : new String[]{null, "", " ", "x".repeat(8193)}) {
            assertThrows(LoginException.class, () -> manager.login(new ApiKeyLogin(value)));
        }
        when(keys.authenticate(new ApiKeyLogin("failed"))).thenThrow(new IllegalStateException("模拟密钥存储故障"));
        assertThrows(IllegalStateException.class, () -> manager.login(new ApiKeyLogin("failed")));
        when(keys.authenticate(new ApiKeyLogin("malformed"))).thenReturn(mock(IUserDetails.class));
        assertThrows(IllegalStateException.class, () -> manager.login(new ApiKeyLogin("malformed")));
        verify(passwords, never()).authenticate(any(PasswordLogin.class));
        verifyNoInteractions(codec, users);
        verify(keys, times(3)).authenticate(any(ApiKeyLogin.class));
    }

    @Test void requestTypesAreExplicitDefaultTheDeviceAndRedactCredentials() {
        assertEquals("default", new PasswordLogin("name", "secret").deviceType());
        assertEquals("default", new ApiKeyLogin("secret", null).deviceType());
        assertFalse(new PasswordLogin("name", "secret", "web").toString().contains("secret"));
        assertFalse(new ApiKeyLogin("secret", "server").toString().contains("secret"));
        UserStore users = mock(UserStore.class);
        JwtTokenCodec codec = mock(JwtTokenCodec.class);
        var manager = new JwtSecurityManager(users, codec, properties(), registry(users));
        assertThrows(IllegalArgumentException.class, () -> manager.login(null));
        assertThrows(IllegalArgumentException.class, () -> manager.login(new ApiKeyLogin("key", " ")));
        assertThrows(IllegalStateException.class, () -> manager.login(() -> "web"));
        assertThrows(UnsupportedOperationException.class,
                () -> new com.yulinlin.security.auth.AnonymousSecurityManager().login(new ApiKeyLogin("key")));
        verifyNoInteractions(users, codec);
    }

    @Test void missingRejectedOrMalformedAuthenticationNeverReachesTokenGeneration() {
        UserStore users = mock(UserStore.class);
        JwtTokenCodec codec = mock(JwtTokenCodec.class);
        LoginAuthenticator<PasswordLogin> authentication = mockAuthenticator(PasswordLogin.class, "password");
        JwtSecurityManager missing = new JwtSecurityManager(users, codec, properties(), registry(users));
        assertThrows(IllegalStateException.class, () -> missing.login(new PasswordLogin("alice", "password")));

        JwtSecurityManager manager = new JwtSecurityManager(users, codec, properties(), registry(users, authentication));
        assertThrows(LoginException.class, () -> manager.login(new PasswordLogin("alice", "wrong")));
        IUserDetails malformed = mock(IUserDetails.class); // 缺少 userId
        when(authentication.authenticate(new PasswordLogin("alice", "password"))).thenReturn(malformed);
        assertThrows(IllegalStateException.class, () -> manager.login(new PasswordLogin("alice", "password")));
        when(authentication.authenticate(new PasswordLogin("alice", "password"))).thenThrow(new IllegalStateException("用户服务不可用"));
        assertThrows(IllegalStateException.class, () -> manager.login(new PasswordLogin("alice", "password")));
        assertNull(SecurityContext.getUser());
        verifyNoInteractions(codec, users);
    }

    @Test void trustedIdLoginLoadsByIdAndPreservesItsAuthenticationSource() {
        UserStore users = mock(UserStore.class);
        IUserDetails user = user("db-user-42");
        when(users.load("db-user-42")).thenReturn(user);
        var manager = new JwtSecurityManager(users, properties(), registry(users));
        var result = manager.login(new UserIdLogin("db-user-42", "web"));
        assertSame(user, result.user());
        assertEquals("user-id", result.authType());
        assertEquals("user-id", SecurityContext.getAuthType());
        assertEquals("user-id", manager.resolveSession(result.token()).authType());
        verify(users, times(2)).load("db-user-42");
        assertEquals("default", new UserIdLogin("db-user-42").deviceType());
    }

    @Test void missingOrMismatchedIdCannotIssueATokenAndCustomIdAuthenticatorReplacesDefault() {
        UserStore users = mock(UserStore.class);
        JwtTokenCodec codec = mock(JwtTokenCodec.class);
        var manager = new JwtSecurityManager(users, codec, properties(), registry(users));
        assertThrows(LoginException.class, () -> manager.login(new UserIdLogin("missing")));
        when(users.load("u1")).thenReturn(user("another-id"));
        assertThrows(IllegalStateException.class, () -> manager.login(new UserIdLogin("u1")));
        assertNull(SecurityContext.getUser());
        verifyNoInteractions(codec);

        var custom = authenticator(UserIdLogin.class, "trusted-id", request -> user(request.userId()));
        var replacement = new JwtSecurityManager(users, properties(), registry(users, custom));
        clearInvocations(users);
        assertEquals("trusted-id", replacement.login(new UserIdLogin("u1")).authType());
        verifyNoInteractions(users); // 自定义认证器替换默认 ID 加载，不执行两个认证器
    }

    @Test void customRequestUsesRegisteredNameAndNamesOrRequestTypesMustBeUnique() {
        UserStore users = mock(UserStore.class);
        var sms = authenticator(SmsLogin.class, "business-sms", request -> user("sms-user"));
        var manager = new JwtSecurityManager(users, properties(), registry(users, sms));
        var result = manager.login(new SmsLogin("verified-code", "web"));
        assertEquals("business-sms", result.authType());
        assertEquals("business-sms", new com.yulinlin.security.jwt.DefaultJwtTokenCodec(properties())
                .parse(result.token()).authType());

        assertThrows(IllegalStateException.class, () -> registry(users, sms, sms));
        var duplicateName = authenticator(PasswordLogin.class, "business-sms", request -> user("u1"));
        assertThrows(IllegalStateException.class, () -> registry(users, sms, duplicateName));
        var invalidName = authenticator(PasswordLogin.class, "bad name", request -> user("u1"));
        assertThrows(IllegalArgumentException.class, () -> registry(users, invalidName));
    }

    @Test void jwtWithoutAuthenticationSourceIsRejected() {
        var now = java.time.Instant.now();
        String oldToken = io.jsonwebtoken.Jwts.builder().subject("u1").id("old-token-id")
                .issuer(properties().getIssuer()).claim("deviceType", "web")
                .issuedAt(java.util.Date.from(now.minusSeconds(1)))
                .expiration(java.util.Date.from(now.plusSeconds(300)))
                .signWith(new javax.crypto.spec.SecretKeySpec(new byte[32], "HmacSHA256"),
                        io.jsonwebtoken.Jwts.SIG.HS256).compact();
        assertNull(new com.yulinlin.security.jwt.DefaultJwtTokenCodec(properties()).parse(oldToken));
    }

    private record SmsLogin(String code, String deviceType) implements LoginRequest { }

    private static IUserDetails user(String id) {
        IUserDetails user = mock(IUserDetails.class);
        when(user.getUserId()).thenReturn(id);
        return user;
    }

    private static LoginAuthenticatorRegistry registry(UserStore users, LoginAuthenticator<?>... authenticators) {
        return new LoginAuthenticatorRegistry(java.util.List.of(authenticators), users);
    }

    private static JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }
}

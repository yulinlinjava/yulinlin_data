package com.yulinlin.regression.security;

import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.auth.UserAuthenticator;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.jwt.JwtProperties;
import com.yulinlin.security.jwt.JwtSecurityManager;
import com.yulinlin.security.jwt.JwtTokenCodec;
import com.yulinlin.security.satoken.SaTokenSecurityManager;
import com.yulinlin.security.spring.SpringSecurityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 统一登录入口与认证边界，不连接外部服务。 */
@ResourceLock("SecurityUtil")
class UsernameLoginEntryTest {
    @AfterEach void cleanup() { SecurityContext.clear(); }

    @Test void noPublicIdLoginRemainsOnTheProtocolFacadeOrBuiltInManagers() {
        for (Class<?> type : new Class<?>[]{ISecurityManager.class, SecurityUtil.class,
                JwtSecurityManager.class, SaTokenSecurityManager.class, SpringSecurityManager.class}) {
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("login", String.class));
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("login", String.class, String.class));
            assertDoesNotThrow(() -> type.getMethod("loginByUsername", String.class, String.class));
            assertDoesNotThrow(() -> type.getMethod("loginByUsername", String.class, String.class, String.class));
        }
    }

    @Test void jwtUsesTheVerifiedUserIdNotTheUsernameAndDoesNotReloadTheAuthenticationResult() {
        UserStore users = mock(UserStore.class);
        UserAuthenticator authentication = mock(UserAuthenticator.class);
        IUserDetails user = mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("db-user-42");
        when(authentication.authenticate("alice", " password ")).thenReturn(user);
        JwtSecurityManager manager = new JwtSecurityManager(users, properties(), authentication);
        var result = manager.loginByUsername("alice", " password ");
        assertSame(user, result.user());
        assertEquals("db-user-42", SecurityContext.getUserId());
        verify(authentication).authenticate("alice", " password ");
        verifyNoInteractions(users); // 认证返回的安全 DTO 可直接使用；后续请求再交给 UserStore
        var codec = new com.yulinlin.security.jwt.DefaultJwtTokenCodec(properties());
        assertEquals("db-user-42", codec.parse(result.token()).userId());
        assertEquals("default", codec.parse(result.token()).deviceType());
    }

    @Test void missingRejectedOrMalformedAuthenticationNeverReachesTokenGeneration() {
        UserStore users = mock(UserStore.class);
        JwtTokenCodec codec = mock(JwtTokenCodec.class);
        UserAuthenticator authentication = mock(UserAuthenticator.class);
        JwtSecurityManager missing = new JwtSecurityManager(users, codec, properties());
        assertThrows(IllegalStateException.class, () -> missing.loginByUsername("alice", "password"));

        JwtSecurityManager manager = new JwtSecurityManager(users, codec, properties(), authentication);
        assertThrows(LoginException.class, () -> manager.loginByUsername("alice", "wrong"));
        IUserDetails malformed = mock(IUserDetails.class); // 缺少 userId
        when(authentication.authenticate("alice", "password")).thenReturn(malformed);
        assertThrows(IllegalArgumentException.class, () -> manager.loginByUsername("alice", "password"));
        when(authentication.authenticate("alice", "password")).thenThrow(new IllegalStateException("用户服务不可用"));
        assertThrows(IllegalStateException.class, () -> manager.loginByUsername("alice", "password"));
        assertNull(SecurityContext.getUser());
        verifyNoInteractions(codec, users);
    }

    private static JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }
}

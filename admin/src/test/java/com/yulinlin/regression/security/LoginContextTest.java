package com.yulinlin.regression.security;

import com.yulinlin.admin.rbac.auth.AdminAuthenticationService;
import com.yulinlin.admin.rbac.auth.AdminUserStore;
import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.security.auth.*;
import com.yulinlin.security.jwt.JwtProperties;
import com.yulinlin.security.jwt.JwtSecurityManager;
import com.yulinlin.security.web.LoginContextInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 源码回归：不启动 Spring、不连接数据库或 Redis。 */
class LoginContextTest {
    @AfterEach void cleanup() { SecurityContext.clear(); }

    @Test void restoresOnceAndRemovesOnSuccessErrorAndAsyncHandoff() throws Exception {
        ISecurityManager manager = mock(ISecurityManager.class);
        LoginSession session = session("token");
        when(manager.resolveSession("token")).thenReturn(session);
        LoginContextInterceptor interceptor = new LoginContextInterceptor(() -> manager);
        MockHttpServletRequest request = request("Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (int mode = 0; mode < 3; mode++) {
            assertTrue(interceptor.preHandle(request, response, this));
            assertSame(session.user(), SecurityContext.getUser());
            assertSame(session.user(), SecurityContext.requireUser());
            assertEquals("u1", SecurityContext.getUserId());
            if (mode == 2) interceptor.afterConcurrentHandlingStarted(request, response, this);
            else interceptor.afterCompletion(request, response, this,
                    mode == 0 ? null : new RuntimeException("模拟业务异常"));
            assertFalse(SecurityContext.isLogin());
            assertNull(SecurityContext.getToken());
        }
        verify(manager, times(3)).resolveSession("token");
        verify(manager, never()).login(anyString(), anyString());
        assertThrows(LoginException.class, SecurityContext::requireUser);
    }

    @Test void failedPreHandleAndAnonymousRequestsCannotKeepOldIdentity() {
        ISecurityManager manager = mock(ISecurityManager.class);
        LoginContextInterceptor interceptor = new LoginContextInterceptor(() -> manager);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContext.set(session("old"));
        assertTrue(interceptor.preHandle(request(null), response, this));
        assertNull(SecurityContext.getUser());
        for (String header : new String[]{"Bearer ", "Basic abc", "Bearer a b", "Bearer missing"}) {
            SecurityContext.set(session("old"));
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> interceptor.preHandle(request(header), response, this));
            assertEquals(401, error.getStatusCode().value());
            assertNull(SecurityContext.getUser());
        }
        when(manager.resolveSession("expired")).thenReturn(
                new LoginSession("expired", Instant.now().minusSeconds(1), session("x").user()));
        assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request("Bearer expired"), response, this));
        when(manager.resolveSession("bad")).thenAnswer(call -> {
            SecurityContext.set(session("partial"));
            throw new IllegalStateException("会话存储异常");
        });
        assertThrows(IllegalStateException.class,
                () -> interceptor.preHandle(request("Bearer bad"), response, this));
        assertNull(SecurityContext.getUser());
        assertTrue(new LoginContextInterceptor(AnonymousSecurityManager::new)
                .preHandle(request("Basic external"), response, this));
    }

    @Test void threadContextDoesNotPropagateAndExpiredSessionsAreRejected() throws Exception {
        SecurityContext.set(session("token"));
        // 使用可观测结果，子线程失败不能静默遗漏。
        java.util.concurrent.atomic.AtomicReference<IUserDetails> found = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> found.set(SecurityContext.getUser()));
        worker.start();
        worker.join();
        assertNull(found.get());
        assertTrue(SecurityContext.isLogin());
        assertThrows(LoginException.class, () -> SecurityContext.set(
                new LoginSession("expired", Instant.now().minusSeconds(1), session("x").user())));
        assertFalse(SecurityContext.isLogin());
    }

    @Test void statelessAdminLoginSurvivesLogoutAndManagerRecreationButChecksCurrentUser() {
        UserRepository users = mock(UserRepository.class);
        PasswordHash passwords = mock(PasswordHash.class);
        when(passwords.encode(anyString())).thenReturn("dummy");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        UserEntity user = new UserEntity();
        user.setId("u1");
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ENABLED);
        when(users.findByUsernameEq("alice")).thenReturn(user);
        when(users.findByIdEq("u1")).thenReturn(user);
        when(users.findSnapshotById("u1")).thenReturn(user);
        AdminAuthenticationService authentication = new AdminAuthenticationService(users, passwords);
        JwtProperties properties = new JwtProperties();
        properties.setSecret(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        JwtSecurityManager manager = new JwtSecurityManager(new AdminUserStore(users), properties);
        LoginResult result = manager.login(authentication.authenticate("alice", "correct", null), "web");
        assertEquals(3, result.token().split("\\.").length);
        assertSame(result.user(), manager.getUserDetails());
        assertEquals(result.token(), SecurityContext.getToken());
        assertFalse(result.toString().contains(result.token()));
        SecurityContext.clear(); // 请求结束仅清理线程，JWT 不保存在服务端
        assertNotNull(manager.resolveSession(result.token()));
        LoginContextInterceptor interceptor = new LoginContextInterceptor(() -> manager);
        interceptor.preHandle(request("Bearer " + result.token()), new MockHttpServletResponse(), this);
        manager.refresh("u1");
        assertEquals("u1", SecurityContext.getUserId());
        assertEquals(result.expiresAt(), SecurityContext.getSession().expiresAt());
        manager.logout();
        assertNull(SecurityContext.getUser());
        assertNotNull(manager.resolveSession(result.token())); // 注销不会撤销已签发的 JWT
        JwtSecurityManager restarted = new JwtSecurityManager(new AdminUserStore(users), properties);
        assertNotNull(restarted.resolveSession(result.token())); // 无需恢复任何登录记录
        assertEquals(result.expiresAt(), restarted.resolveSession(result.token()).expiresAt());
        JwtProperties otherProperties = new JwtProperties();
        byte[] otherKey = new byte[32];
        java.util.Arrays.fill(otherKey, (byte) 1);
        otherProperties.setSecret(java.util.Base64.getEncoder().encodeToString(otherKey));
        assertNull(new JwtSecurityManager(new AdminUserStore(users), otherProperties)
                .resolveSession(result.token())); // 无状态不代表放宽验签
        SecurityContext.set(session("old"));
        assertThrows(LoginException.class, () -> authentication.authenticate("alice", "wrong", null));
        assertNull(SecurityContext.getUser());

        LoginResult first = manager.login("u1", "web");
        LoginResult second = manager.login("u1", "android");
        user.setNickname("新昵称");
        assertEquals("新昵称", manager.resolveSession(first.token()).user().getAttribute("nickname"));
        RoleEntity role = new RoleEntity();
        role.setCode("reader");
        role.setPermissions(java.util.List.of("user:query"));
        user.setRoles(java.util.List.of(role));
        assertTrue(manager.resolveSession(first.token()).user().hasPermission("user:query"));
        role.setPermissions(java.util.List.of());
        assertFalse(manager.resolveSession(first.token()).user().hasPermission("user:query"));
        user.setPasswordHash("new-hash");
        assertNotNull(manager.resolveSession(first.token())); // 密码变更不撤销 JWT
        assertNotNull(manager.resolveSession(second.token()));
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.DISABLED);
        assertNull(manager.resolveSession(first.token()));
        assertNull(manager.refresh()); // 刷新时重新检查当前用户状态
        assertNull(SecurityContext.getUser());
        assertThrows(LoginException.class, () -> authentication.authenticate("alice", "correct", null));
        assertThrows(LoginException.class, () -> manager.login("u1", "web"));
        user.setStatus(UserStatus.ENABLED);
        assertNotNull(manager.resolveSession(first.token())); // 恢复账号后未过期的 Token 仍可用
        when(users.findSnapshotById("u1")).thenReturn(null);
        assertNull(manager.resolveSession(first.token()));
    }

    private static LoginSession session(String token) {
        IUserDetails user = mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("u1");
        return new LoginSession(token, Instant.now().plusSeconds(300), user);
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/example");
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }
}

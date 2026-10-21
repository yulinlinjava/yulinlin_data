package com.yulinlin.regression.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.SecurityLoginAutoConfiguration;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiRole;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.spring.DefaultSpringUserMapper;
import com.yulinlin.security.spring.SpringLoginInterceptor;
import com.yulinlin.security.spring.SpringSecurityAdapterAutoConfiguration;
import com.yulinlin.security.spring.SpringSecurityManager;
import com.yulinlin.security.spring.SpringUserMapper;
import com.yulinlin.security.web.LoginInterceptor;
import com.yulinlin.security.web.LoginContextInterceptor;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 现有 Spring 身份桥接回归；不启动实际服务器，不读取 YAML，不连接数据库。 */
@ResourceLock("SecurityUtil")
@ResourceLock("SpringSecurityContextHolder")
class SpringSecurityContextTest {
    private Object previousBinding;

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                AopAutoConfiguration.class, SpringSecurityAdapterAutoConfiguration.class,
                SecurityAuthAutoConfiguration.class, SecurityLoginAutoConfiguration.class));
    }

    @BeforeEach void prepare() {
        previousBinding = ReflectionTestUtils.getField(SecurityUtil.class, "active");
        ReflectionTestUtils.setField(SecurityUtil.class, "active", null);
        SecurityContext.clear();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach void cleanup() {
        SecurityContext.clear();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        ReflectionTestUtils.setField(SecurityUtil.class, "active", previousBinding);
    }

    @Test void autoConfigurationNeedsNeitherUserStoreNorAnotherTokenAndMapsOnlySafeFields() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ISecurityManager.class);
            assertInstanceOf(SpringLoginInterceptor.class, context.getBean(LoginInterceptor.class));
            var principal = User.withUsername("alice").password("sensitive-password")
                    .authorities("ROLE_ADMIN", "video:edit").build();
            authenticate(UsernamePasswordAuthenticationToken.authenticated(principal, "sensitive-credential",
                    principal.getAuthorities()));
            IUserDetails user = SecurityContext.requireUser();
            assertEquals("alice", user.getUserId());
            assertEquals(Set.of("ADMIN"), user.getRoles());
            assertTrue(SecurityUtil.hasPermission("video:edit"));
            assertTrue(SecurityUtil.hasRole("ADMIN"));
            assertFalse(SecurityUtil.hasRole("admin"));
            assertNull(SecurityContext.getToken());
            assertNull(SecurityContext.getSession().expiresAt());
            assertNull(localSnapshot()); // 不依赖 MVC 收尾，原生过滤器提前返回也不遗留框架线程数据
            String json = new ObjectMapper().writeValueAsString(user);
            assertFalse(json.contains("sensitive"));
            assertFalse(json.contains("password"));
            assertFalse(json.contains("credentials"));
        });
    }

    @Test void anonymousUnauthenticatedSwitchedAndLoggedOutIdentitiesAreNotStale() {
        runner().run(context -> {
            authenticate(token("first", "ROLE_USER"));
            assertEquals("first", SecurityContext.getUserId());
            authenticate(token("second", "ROLE_ADMIN"));
            assertEquals("second", SecurityContext.getUserId());
            assertFalse(SecurityContext.getUser().hasRole("USER"));
            authenticate(new AnonymousAuthenticationToken("key", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
            assertFalse(SecurityContext.isLogin()); // AnonymousAuthenticationToken.isAuthenticated() 本身为 true
            authenticate(UsernamePasswordAuthenticationToken.unauthenticated("pending", "password"));
            assertFalse(SecurityContext.isLogin());
            authenticate(token("second", "ROLE_ADMIN"));
            assertNotNull(SecurityContext.getUser());
            SecurityContextHolder.clearContext();
            assertNull(SecurityContext.getUser());
        });
    }

    @Test void permissionsUseCurrentSpringAuthenticationAndExistingAnnotationsStillWork() {
        runner().withBean(ProtectedService.class).run(context -> {
            ProtectedService service = context.getBean(ProtectedService.class);
            authenticate(token("u1", "ROLE_ADMIN", "video:edit"));
            assertEquals("ok", service.edit());
            authenticate(token("u1", "ROLE_ADMIN")); // 同一用户换了权限也不能复用旧快照
            assertEquals(403, assertThrows(ResponseStatusException.class, service::edit).getStatusCode().value());
            SecurityContextHolder.clearContext();
            assertEquals(401, assertThrows(ResponseStatusException.class, service::edit).getStatusCode().value());
        });
    }

    @Test void mapperCanDefineDatabaseIdAndCustomInterceptorStillWins() {
        SpringUserMapper mapper = authentication -> new DefaultSpringUserMapper.UserSnapshot(
                "db-42", authentication.getName(), Set.of("editor"), Set.of("video:edit"));
        LoginInterceptor custom = new LoginInterceptor() { };
        runner().withBean(SpringUserMapper.class, () -> mapper)
                .withBean(LoginInterceptor.class, () -> custom).run(context -> {
                    assertSame(custom, context.getBean(LoginInterceptor.class));
                    assertThat(context).doesNotHaveBean(DefaultSpringUserMapper.class);
                    authenticate(token("alice", "ROLE_EDITOR"));
                    assertEquals("db-42", SecurityUtil.getUserId());
                    assertTrue(SecurityUtil.hasRole("editor"));
                    assertEquals("db-42", SecurityUtil.refresh().getUserId());
                    assertThrows(NoSuchMethodException.class,
                            () -> SecurityUtil.class.getMethod("login", String.class, String.class));
                    assertEquals("db-42", SecurityUtil.getUserId());
                });
    }

    @Test void optionalUserStoreOnlyLoadsRequestedUserAndCannotChangeCurrentAuthentication() {
        UserStore store = mock(UserStore.class);
        when(store.load("other")).thenReturn(new DefaultSpringUserMapper.UserSnapshot(
                "other", "其他用户", Set.of(), Set.of()));
        runner().withBean(UserStore.class, () -> store).run(context -> {
            authenticate(token("current"));
            assertEquals("current", SecurityUtil.getUserId());
            verifyNoInteractions(store);
            assertEquals("other", SecurityUtil.getUserDetails("other").getUserId());
            assertEquals("current", SecurityUtil.getUserId());
            assertNull(SecurityUtil.getUserDetails("missing"));
        });
    }

    @Test void springNativeLogoutAndUnifiedLogoutBothClearTheBridge() {
        runner().run(context -> {
            MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/logout");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicInteger delegated = new AtomicInteger();
            var springRequest = new SecurityContextHolderAwareRequestWrapper(raw, "ROLE_") {
                @Override public void logout() {
                    delegated.incrementAndGet();
                    new SecurityContextLogoutHandler().logout(this, response,
                            SecurityContextHolder.getContext().getAuthentication());
                }
            };
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                    new HttpServletRequestWrapper(springRequest), response));
            authenticate(token("u1", "ROLE_USER"));
            raw.getSession();
            assertNotNull(SecurityContext.getUser());
            SecurityUtil.logout();
            assertEquals(1, delegated.get());
            assertNull(raw.getSession(false));
            assertFalse(SecurityContext.isLogin());
            authenticate(token("u2", "ROLE_USER"));
            assertEquals("u2", SecurityContext.getUserId());
            new SecurityContextLogoutHandler().logout(raw, response,
                    SecurityContextHolder.getContext().getAuthentication());
            assertFalse(SecurityContext.isLogin());
        });
    }

    @Test void logoutWithoutNativeServletSupportMustNotPretendToSucceed() {
        runner().run(context -> {
            authenticate(token("u1"));
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            assertThrows(IllegalStateException.class, SecurityUtil::logout);
            assertEquals("u1", SecurityContext.getUserId());
        });
    }

    @Test void servletLogoutThatDoesNotClearAuthenticationCannotReportSuccess() {
        runner().run(context -> {
            var request = new SecurityContextHolderAwareRequestWrapper(new MockHttpServletRequest(), "ROLE_") {
                @Override public void logout() { /* 模拟未正确安装原生注销处理器 */ }
            };
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            authenticate(token("u1"));
            assertThrows(IllegalStateException.class, SecurityUtil::logout);
            assertEquals("u1", SecurityContext.getUserId());
        });
    }

    @Test void interceptorDoesNotParseCredentialsOrClearSpringOwnedContext() {
        runner().run(context -> {
            authenticate(token("u1"));
            LoginInterceptor interceptor = context.getBean(LoginInterceptor.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/profile");
            request.addHeader("Authorization", "Basic handled-by-spring");
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(request, response, this));
            assertEquals("u1", SecurityContext.getUserId());
            interceptor.afterCompletion(request, response, this, null);
            assertNull(localSnapshot());
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("u1", SecurityContext.getUserId());
            interceptor.afterConcurrentHandlingStarted(request, response, this);
            assertNull(localSnapshot());
            // 默认 Spring ThreadLocal 模式没有身份继承，框架也不会主动复制身份。
            AtomicReference<IUserDetails> user = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try { user.set(SecurityContext.getUser()); }
                catch (Throwable error) { failure.set(error); }
                finally { SecurityContext.clear(); SecurityContextHolder.clearContext(); }
            });
            thread.start();
            thread.join();
            assertNull(failure.get());
            assertNull(user.get());
        });
    }

    @Test void failedMappingNeverReturnsAnEarlierIdentity() {
        SpringUserMapper mapper = mock(SpringUserMapper.class);
        when(mapper.map(any())).thenReturn(new DefaultSpringUserMapper.UserSnapshot("u1", "user", Set.of(), Set.of()));
        runner().withBean(SpringUserMapper.class, () -> mapper).run(context -> {
            authenticate(token("u1"));
            assertEquals("u1", SecurityContext.getUserId());
            when(mapper.map(any())).thenThrow(new IllegalStateException("模拟映射失败"));
            assertThrows(IllegalStateException.class, SecurityContext::getUser);
            assertNull(localSnapshot());
        });
    }

    @Test void customManagerBacksOffEntireSpringAdapter() {
        ISecurityManager custom = mock(ISecurityManager.class);
        runner().withBean(ISecurityManager.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(SpringSecurityManager.class)
                    .doesNotHaveBean(SpringUserMapper.class);
            assertSame(custom, SecurityUtil.manager());
        });
    }

    @Test void tokenlessExternalSnapshotCannotBypassBearerTokenValidation() {
        ISecurityManager manager = mock(ISecurityManager.class);
        when(manager.resolveSession("supplied")).thenReturn(new LoginSession(null, null,
                new DefaultSpringUserMapper.UserSnapshot("u1", "user", Set.of(), Set.of())));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer supplied");
        assertThrows(ResponseStatusException.class, () -> new LoginContextInterceptor(() -> manager)
                .preHandle(request, new MockHttpServletResponse(), this));
        assertNull(localSnapshot());
    }

    public static class ProtectedService {
        @ApiRole("ADMIN") @ApiPerm("video:edit")
        public String edit() { return "ok"; }
    }

    private static Authentication token(String userId, String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(userId, null,
                AuthorityUtils.createAuthorityList(authorities));
    }

    private static void authenticate(Authentication authentication) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private static LoginSession localSnapshot() {
        return (LoginSession) ((ThreadLocal<?>) ReflectionTestUtils.getField(SecurityContext.class, "CURRENT")).get();
    }
}

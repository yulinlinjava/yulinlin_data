package com.yulinlin.regression.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.mock.SaStorageForMock;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.SecurityLoginAutoConfiguration;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.auth.LoginAuthenticator;
import com.yulinlin.security.auth.UserIdLogin;
import com.yulinlin.security.satoken.SaTokenSecurityManager;
import com.yulinlin.security.auth.ApiKeyLogin;
import com.yulinlin.security.auth.PasswordLogin;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.satoken.SaTokenAutoConfiguration;
import com.yulinlin.security.satoken.SaTokenLoginInterceptor;
import com.yulinlin.security.web.LoginInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static com.yulinlin.regression.security.LoginTestSupport.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 原生 StpUtil 集成回归；仅内存会话，不连接数据库或 Redis。 */
@ResourceLock("SecurityUtil")
@ResourceLock("SaToken")
class SaTokenContextTest {
    private final UserStore users = mock(UserStore.class);
    private StpLogic previousLogic;
    private SaTokenContext previousContext;
    private SaTokenDao previousDao;
    private Object previousBinding;
    private SaTokenConfig config;

    @BeforeEach void prepare() {
        previousBinding = ReflectionTestUtils.getField(SecurityUtil.class, "active");
        ReflectionTestUtils.setField(SecurityUtil.class, "active", null);
        previousLogic = StpUtil.getStpLogic();
        previousContext = SaManager.getSaTokenContext();
        previousDao = SaManager.getSaTokenDao();
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        config = new SaTokenConfig().setTokenName("test-token").setTimeout(3600)
                .setIsReadHeader(true).setIsReadCookie(true).setIsReadBody(false).setIsLog(false);
        StpUtil.setStpLogic(new StpLogic("login").setConfig(config));
        when(users.load(anyString())).thenAnswer(call -> user(call.getArgument(0)));
        beginRequest(new MockHttpServletRequest("POST", "/login"));
        SecurityContext.clear();
    }

    @AfterEach void cleanup() {
        SecurityContext.clear();
        SaHolder.getContext().clearContext();
        SaManager.setSaTokenContext(previousContext);
        SaManager.setSaTokenDao(previousDao);
        StpUtil.setStpLogic(previousLogic);
        ReflectionTestUtils.setField(SecurityUtil.class, "active", previousBinding);
    }

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SaTokenAutoConfiguration.class,
                        SecurityAuthAutoConfiguration.class, SecurityLoginAutoConfiguration.class))
                .withBean(UserStore.class, () -> users);
    }

    @Test void nativeLoginLogoutAndSwitchAccountAreImmediatelyVisible() {
        runner().run(context -> {
            assertNull(context.getStartupFailure());
            assertInstanceOf(SaTokenLoginInterceptor.class, context.getBean(LoginInterceptor.class));
            assertFalse(SecurityContext.isLogin());
            trustedNativeLogin("u1"); // 不调用框架 login/set
            assertEquals("u1", SecurityContext.getUserId());
            assertEquals(StpUtil.getTokenValue(), SecurityContext.getToken());
            assertSame(SecurityContext.getUser(), SecurityUtil.getUserDetails());
            verify(users, times(1)).load("u1");
            trustedNativeLogin("u2");
            assertEquals("u2", SecurityContext.getUserId());
            verify(users, times(1)).load("u2");
            StpUtil.logout();
            assertFalse(SecurityContext.isLogin());
            assertNull(SecurityContext.getUser());
            assertNull(SecurityContext.getToken());
        });
    }

    @Test void frameworkLoginAndNativeLogoutShareTheSameToken() {
        LoginAuthenticator<PasswordLogin> authenticator = mockAuthenticator(PasswordLogin.class, "password");
        when(authenticator.authenticate(new PasswordLogin("alice", "correct", "web"))).thenReturn(user("u1"));
        runner().withBean("passwordAuthenticator", LoginAuthenticator.class, () -> authenticator).run(context -> {
            var result = SecurityUtil.login(new PasswordLogin("alice", "correct", "web"));
            verify(authenticator).authenticate(new PasswordLogin("alice", "correct", "web"));
            assertEquals(result.token(), StpUtil.getTokenValue());
            assertEquals("u1", StpUtil.getLoginIdAsString());
            assertEquals("password", result.authType());
            assertEquals("password", SecurityUtil.getAuthType());
            StpUtil.logout();
            assertFalse(SecurityUtil.isLogin());
            trustedNativeLogin("u2");
            SecurityUtil.logout();
            assertFalse(StpUtil.isLogin());
            assertFalse(SecurityContext.isLogin());
        });
    }

    @Test void apiKeyBeanCreatesANativeSessionThatCanBeRestoredAndLoggedOut() {
        LoginAuthenticator<ApiKeyLogin> keys = mockAuthenticator(ApiKeyLogin.class, "api-key");
        when(keys.authenticate(new ApiKeyLogin("partner-secret", "server"))).thenReturn(user("partner-1"));
        runner().withBean("apiKeyAuthenticator", LoginAuthenticator.class, () -> keys).run(context -> {
            var result = SecurityUtil.login(new ApiKeyLogin("partner-secret", "server"));
            assertEquals("partner-1", StpUtil.getLoginIdAsString());
            assertEquals(result.token(), StpUtil.getTokenValue());
            verifyNoInteractions(users);
            SecurityContext.clear();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/protected");
            request.addHeader("test-token", result.token());
            beginRequest(request);
            var interceptor = context.getBean(LoginInterceptor.class);
            interceptor.preHandle(request, new MockHttpServletResponse(), this);
            assertEquals("partner-1", SecurityUtil.getUserId());
            assertEquals("api-key", SecurityContext.getAuthType());
            verify(users).load("partner-1");
            verify(keys, times(1)).authenticate(new ApiKeyLogin("partner-secret", "server"));
            SecurityUtil.logout();
            assertNull(StpUtil.getLoginIdByToken(result.token()));
            assertFalse(SecurityUtil.isLogin());
        });
    }

    @Test void missingOrInvalidApiKeyCannotCreateANativeSession() {
        runner().run(context -> {
            assertThrows(IllegalStateException.class, () -> SecurityUtil.login(new ApiKeyLogin("key")));
            assertFalse(StpUtil.isLogin());
        });
        runner().withBean("apiKeyAuthenticator", LoginAuthenticator.class,
                () -> authenticator(ApiKeyLogin.class, "api-key", request -> null)).run(context -> {
            assertThrows(LoginException.class, () -> SecurityUtil.login(new ApiKeyLogin("bad-key")));
            assertFalse(StpUtil.isLogin());
        });
    }

    @Test void missingOrRejectedAccountAuthenticationCannotCreateANativeSession() {
        runner().run(context -> {
            assertThrows(IllegalStateException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
            assertFalse(StpUtil.isLogin());
        });
        runner().withBean("passwordAuthenticator", LoginAuthenticator.class,
                () -> authenticator(PasswordLogin.class, "password", request -> null)).run(context -> {
            assertThrows(com.yulinlin.security.auth.LoginException.class,
                    () -> SecurityUtil.login(new PasswordLogin("alice", "wrong")));
            assertFalse(StpUtil.isLogin());
        });
    }

    @Test void nextRequestUsesNativeHeaderPrefixCookieOrLegacyBearer() {
        runner().run(context -> {
            trustedNativeLogin("u1");
            String token = StpUtil.getTokenValue();
            LoginInterceptor interceptor = context.getBean(LoginInterceptor.class);
            for (String source : new String[]{"header", "cookie", "bearer"}) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/profile");
                if (source.equals("header")) {
                    config.setTokenPrefix("Token");
                    request.addHeader("test-token", "Token " + token);
                } else {
                    config.setTokenPrefix(null);
                    if (source.equals("cookie")) request.setCookies(new jakarta.servlet.http.Cookie("test-token", token));
                    else request.addHeader("Authorization", "Bearer " + token);
                }
                beginRequest(request);
                MockHttpServletResponse response = new MockHttpServletResponse();
                assertTrue(interceptor.preHandle(request, response, this));
                assertEquals("u1", SecurityContext.getUserId());
                assertEquals(token, StpUtil.getTokenValue());
                interceptor.afterCompletion(request, response, this, null);
                assertNull(localSnapshot()); // 清理快照不注销原生会话
                SaHolder.getContext().clearContext();
                assertNull(SecurityContext.getUser());
            }
            verify(users, times(3)).load("u1");
            // Bearer 兼容入口也必须支持原生 logout，而不是只清掉框架快照。
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
            request.addHeader("Authorization", "Bearer " + token);
            beginRequest(request);
            interceptor.preHandle(request, new MockHttpServletResponse(), this);
            StpUtil.logout();
            assertFalse(SecurityContext.isLogin());
        });
    }

    @Test void invalidNativeCredentialCannotFallBackToAnotherValidBearer() {
        runner().run(context -> {
            trustedNativeLogin("u1");
            String token = StpUtil.getTokenValue();
            config.setTokenPrefix("Token");
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/profile");
            request.addHeader("test-token", "bad-prefix");
            request.addHeader("Authorization", "Bearer " + token);
            beginRequest(request);
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> context.getBean(LoginInterceptor.class).preHandle(request, new MockHttpServletResponse(), this));
            assertEquals(401, error.getStatusCode().value());
            assertNull(localSnapshot());
        });
    }

    @Test void kickoutAndExpiryCannotReuseCachedUser() {
        runner().run(context -> {
            trustedNativeLogin("u1");
            assertEquals("u1", SecurityContext.getUserId());
            StpUtil.kickout("u1");
            assertNull(SecurityContext.getUser());
            trustedNativeLogin("u2");
            assertEquals("u2", SecurityContext.getUserId());
            StpUtil.getStpLogic().getSaTokenDao().delete(
                    StpUtil.getStpLogic().splicingKeyTokenValue(StpUtil.getTokenValue()));
            assertFalse(SecurityContext.isLogin());
        });
    }

    @Test void failuresClearOldSnapshotAndUserLoadingCannotReenterContext() {
        runner().run(context -> {
            trustedNativeLogin("u1");
            assertEquals("u1", SecurityContext.getUserId());
            trustedNativeLogin("u2");
            when(users.load("u2")).thenThrow(new IllegalStateException("模拟用户存储不可用"));
            assertThrows(IllegalStateException.class, SecurityContext::getUser);
            assertNull(localSnapshot());
            when(users.load("u3")).thenAnswer(call -> SecurityContext.getUser());
            trustedNativeLogin("u3");
            assertThrows(IllegalStateException.class, SecurityContext::getUser);
            assertNull(localSnapshot());
            when(users.load("u4")).thenReturn(null);
            trustedNativeLogin("u4");
            assertNull(SecurityContext.getUser());
            assertFalse(StpUtil.isLogin());
        });
    }

    @Test void sessionStoreFailureDoesNotFallBackToCachedIdentity() {
        runner().run(context -> {
            trustedNativeLogin("u1");
            assertEquals("u1", SecurityContext.getUserId());
            StpLogic broken = spy(StpUtil.getStpLogic());
            doThrow(new IllegalStateException("模拟会话存储不可用"))
                    .when(broken).getLoginIdByToken(anyString());
            StpUtil.setStpLogic(broken);
            assertThrows(IllegalStateException.class, SecurityContext::getUser);
            assertNull(localSnapshot());
        });
    }

    @Test void contextDoesNotPropagateToNewThreadsAndAsyncHandoffClearsSnapshot() {
        runner().run(context -> {
            trustedNativeLogin("u1");
            assertNotNull(SecurityContext.getUser());
            AtomicReference<IUserDetails> result = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try { result.set(SecurityContext.getUser()); }
                catch (Throwable error) { failure.set(error); }
            });
            worker.start();
            worker.join();
            assertNull(failure.get());
            assertNull(result.get());
            context.getBean(LoginInterceptor.class).afterConcurrentHandlingStarted(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), this);
            assertNull(localSnapshot());
        });
    }

    @Test void customInterceptorStillReplacesDefaultWithoutDisablingNativeBridge() {
        LoginInterceptor custom = new LoginInterceptor() { };
        runner().withBean(LoginInterceptor.class, () -> custom).run(context -> {
            assertSame(custom, context.getBean(LoginInterceptor.class));
            assertTrue(context.getBeansOfType(SaTokenLoginInterceptor.class).isEmpty());
            trustedNativeLogin("u1");
            assertEquals("u1", SecurityContext.getUserId());
        });
    }

    @Test void nativeTokenWithoutAuthenticationSourceIsNotRestored() {
        runner().run(context -> {
            StpUtil.login("u1"); // 有原生会话但没有可信来源元数据
            assertTrue(StpUtil.isLogin());
            assertFalse(SecurityContext.isLogin());
            assertNull(SecurityContext.getAuthType());
            verifyNoInteractions(users);
        });
    }

    @Test void trustedIdLoginIsRegisteredAutomaticallyAndSourcesStayTokenScoped() {
        runner().withBean("passwordAuthenticator", LoginAuthenticator.class,
                () -> authenticator(PasswordLogin.class, "password", request -> user("u1")))
                .run(context -> {
                    var first = SecurityUtil.login(new UserIdLogin("u1", "web"));
                    var second = SecurityUtil.login(new PasswordLogin("alice", "correct", "web"));
                    assertNotEquals(first.token(), second.token());
                    assertEquals("user-id", first.authType());
                    assertEquals("password", second.authType());
                    var manager = context.getBean(SaTokenSecurityManager.class);
                    assertEquals("user-id", manager.resolveSession(first.token()).authType());
                    assertEquals("password", manager.resolveSession(second.token()).authType());
                    assertEquals("password", SecurityContext.getAuthType());
                });
    }

    private static void trustedNativeLogin(String userId) {
        StpUtil.login(userId, StpUtil.getStpLogic().createSaLoginParameter()
                .setDeviceType("web").setIsShare(false));
        StpUtil.getStpLogic().getTokenSessionByToken(StpUtil.getTokenValue())
                .set(SaTokenSecurityManager.AUTH_TYPE_KEY, "native-password");
    }

    private void beginRequest(MockHttpServletRequest request) {
        SecurityContext.clear();
        SaRequest nativeRequest = mock(SaRequest.class);
        when(nativeRequest.getHeader(anyString())).thenAnswer(call -> request.getHeader(call.getArgument(0)));
        when(nativeRequest.getParam(anyString())).thenAnswer(call -> request.getParameter(call.getArgument(0)));
        when(nativeRequest.getCookieValue(anyString())).thenAnswer(call -> {
            if (request.getCookies() != null) for (var cookie : request.getCookies()) {
                if (cookie.getName().equals(call.getArgument(0))) return cookie.getValue();
            }
            return null;
        });
        SaHolder.getContext().setContext(nativeRequest, mock(SaResponse.class, RETURNS_SELF), new SaStorageForMock());
    }

    private static LoginSession localSnapshot() {
        return (LoginSession) ((ThreadLocal<?>) ReflectionTestUtils.getField(SecurityContext.class, "CURRENT")).get();
    }

    private static IUserDetails user(String userId) {
        IUserDetails user = mock(IUserDetails.class);
        when(user.getUserId()).thenReturn(userId);
        return user;
    }
}

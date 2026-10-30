package com.yulinlin.admin.security;

import com.yulinlin.security.SecurityAuthProperties;
import com.yulinlin.security.SecurityLoginAutoConfiguration;
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.annotation.ApiRole;
import com.yulinlin.security.aop.ApiAuthAspect;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityContextResolver;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.web.LoginContextWebMvcConfigurer;
import com.yulinlin.security.web.LoginInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只使用 MVC 拦截器与方法代理，不启动应用、不连接数据库。 */
class PublicLoginInterceptorTest {
    private ISecurityManager manager;
    private LoginSession valid;
    private AsyncHandlerInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void prepare() {
        SecurityContext.clear();
        manager = mock(ISecurityManager.class, CALLS_REAL_METHODS);
        IUserDetails user = mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("user-1");
        valid = new LoginSession("good", Instant.now().plusSeconds(600), user, "password");
        when(manager.resolveSession("good")).thenReturn(valid);
        when(manager.resolveSession("expired")).thenReturn(
                new LoginSession("expired", Instant.now().minusSeconds(1), user, "password"));
        when(manager.resolveSession("mismatch")).thenReturn(valid);
        when(manager.resolveSession("rejected")).thenThrow(new LoginException("用户已停用"));
        configureTokenHeader("Authorization", "Bearer");
        request = new MockHttpServletRequest("POST", "/public");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void cleanup() { SecurityContext.clear(); }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Bearer good", "Bearer expired", "Bearer missing", "Bearer mismatch",
            "Bearer rejected", "Bearer ", "Bearer a b", "Basic bad"})
    void publicEndpointsAcceptOptionalTokenAndRestoreOnlyValidIdentity(String authorization) throws Exception {
        if (authorization != null) request.addHeader("Authorization", authorization);
        for (HandlerMethod handler : List.of(handler(new PublicApi(), "open"),
                handler(new LoginApi(), "open"), handler(new UnannotatedApi(), "open"),
                handler(new InterfaceApi(), "open"), handler(new InheritedApi(), "open"))) {
            SecurityContext.set(valid); // 模拟线程上残留的上一个请求，不能沿用。
            assertTrue(interceptor.preHandle(request, response, handler));
            if ("Bearer good".equals(authorization)) assertSame(valid, SecurityContext.getSession());
            else assertNull(SecurityContext.getSession());
            assertEquals(200, response.getStatus());
            assertFalse(new ApiAuthAspect(() -> manager).matches(handler.getMethod(), handler.getBeanType()));
            interceptor.afterCompletion(request, response, handler, null);
            assertNull(SecurityContext.getSession());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"token", "X-Token"})
    void configuredHeaderAcceptsRawTokenAndDoesNotReadOtherSources(String header) throws Exception {
        configureTokenHeader(header, "");
        request.addHeader(header, "good");
        request.addHeader("Authorization", "Bearer rejected");
        HandlerMethod handler = handler(new PublicApi(), "open");
        assertTrue(interceptor.preHandle(request, response, handler));
        assertSame(valid, SecurityContext.getSession());
        verify(manager).resolveSession("good");
        verify(manager, never()).resolveSession("rejected");

        clearInvocations(manager);
        request = new MockHttpServletRequest("POST", "/public");
        request.addHeader("Authorization", "Bearer good");
        request.setParameter(header, "good");
        request.setCookies(new jakarta.servlet.http.Cookie(header, "good"));
        assertTrue(interceptor.preHandle(request, response, handler));
        assertNull(SecurityContext.getSession());
        verify(manager, never()).resolveSession(anyString());
    }

    @Test
    void configuredPrefixIsStrippedBeforeVerificationAndMustMatch() throws Exception {
        configureTokenHeader("X-Auth", "Token");
        request.addHeader("x-auth", "tOkEn good");
        HandlerMethod handler = handler(new PublicApi(), "open");
        assertTrue(interceptor.preHandle(request, response, handler));
        assertSame(valid, SecurityContext.getSession());
        verify(manager).resolveSession("good");

        clearInvocations(manager);
        for (String value : List.of("Bearer good", "Tokengood", "Token", "Token ", "Token a b")) {
            request = new MockHttpServletRequest("POST", "/public");
            request.addHeader("X-Auth", value);
            assertTrue(interceptor.preHandle(request, response, handler));
            assertNull(SecurityContext.getSession());
            assertThrows(ResponseStatusException.class,
                    () -> interceptor.preHandle(request, response, handler(new PublicApi(), "login")));
        }
        verify(manager, never()).resolveSession(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"missing", "expired", "rejected", "a b", "Bearer good"})
    void rawTokenHeaderPreservesPublicAndProtectedEndpointRules(String token) throws Exception {
        configureTokenHeader("token", "");
        if (token != null) request.addHeader("token", token);
        assertTrue(interceptor.preHandle(request, response, handler(new PublicApi(), "open")));
        assertNull(SecurityContext.getSession());
        if (token != null) {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                    () -> interceptor.preHandle(request, response, handler(new PublicApi(), "login"))).getStatusCode());
        }
    }

    @Test
    void invalidHeaderAndPrefixConfigurationFailsBeforeServingRequests() {
        SecurityAuthProperties properties = new SecurityAuthProperties();
        assertEquals("Authorization", properties.getTokenHeader());
        assertEquals("Bearer", properties.getTokenPrefix());
        assertThrows(IllegalArgumentException.class, () -> properties.setTokenHeader(null));
        for (String header : List.of("", " ", "X Token", "X:Token", "X\r\nToken", "中文")) {
            assertThrows(IllegalArgumentException.class, () -> properties.setTokenHeader(header));
        }
        assertThrows(IllegalArgumentException.class, () -> properties.setTokenPrefix(null));
        for (String prefix : List.of(" ", "Bearer ", "Token Value", "Token\r\n")) {
            assertThrows(IllegalArgumentException.class, () -> properties.setTokenPrefix(prefix));
        }
        assertDoesNotThrow(() -> properties.setTokenPrefix(""));
    }

    private void configureTokenHeader(String header, String prefix) {
        SecurityAuthProperties properties = new SecurityAuthProperties();
        properties.setTokenHeader(header);
        properties.setTokenPrefix(prefix);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("securityManager", manager);
        // 直接核对工厂传参，不启动 Spring 上下文、不执行 YAML 属性绑定。
        interceptor = registered(new SecurityLoginAutoConfiguration()
                .loginContextInterceptor(beans.getBeanProvider(ISecurityManager.class), properties));
    }

    @Test
    void oversizedTokenIsAnonymousOnlyOnPublicEndpoint() throws Exception {
        request.addHeader("Authorization", "Bearer " + "x".repeat(8193));
        assertTrue(interceptor.preHandle(request, response, handler(new PublicApi(), "open")));
        assertNull(SecurityContext.getSession());
        verify(manager, never()).resolveSession(anyString());
    }

    @Test
    void classPublicDoesNotOverrideMethodLoginRoleOrPermission() throws Exception {
        request.addHeader("Authorization", "Bearer expired");
        for (String name : List.of("login", "role", "permission")) {
            HandlerMethod handler = handler(new PublicApi(), name);
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> interceptor.preHandle(request, response, handler));
            assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
            assertNull(SecurityContext.getSession());
            assertTrue(new ApiAuthAspect(() -> manager).matches(handler.getMethod(), handler.getBeanType()));
        }
    }

    @Test
    void missingTokenStillFailsMethodAuthorizationOnProtectedEndpoint() throws Exception {
        PublicApi target = new PublicApi();
        assertTrue(interceptor.preHandle(request, response, handler(target, "login")));
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new ApiAuthAspect(() -> manager));
        PublicApi proxy = (PublicApi) factory.getProxy();
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, proxy::login).getStatusCode());
    }

    @Test
    void conflictingAnnotationsCannotBecomeAnonymousFallback() throws Exception {
        request.addHeader("Authorization", "Bearer missing");
        HandlerMethod handler = handler(new ConflictingApi(), "open");
        assertThrows(IllegalStateException.class, () -> interceptor.preHandle(request, response, handler));
        assertNull(SecurityContext.getSession());
    }

    @Test
    void customInterceptorAuthenticationRejectionUsesSamePublicRule() throws Exception {
        interceptor = registered(new LoginInterceptor() {
            @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest req,
                    jakarta.servlet.http.HttpServletResponse res, Object handler) {
                SecurityContext.set(valid);
                throw new LoginException("旧凭证无效");
            }
        });
        assertTrue(interceptor.preHandle(request, response, handler(new PublicApi(), "open")));
        assertNull(SecurityContext.getSession());
        assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, handler(new PublicApi(), "login")));
        assertNull(SecurityContext.getSession());
    }

    @Test
    void infrastructureAndUnrelatedHttpErrorsAreNeverSwallowed() throws Exception {
        for (RuntimeException failure : List.of(new IllegalStateException("Redis 不可用"),
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "上游服务认证失败"))) {
            reset(manager);
            when(manager.resolveSession("good")).thenThrow(failure);
            request = new MockHttpServletRequest("POST", "/public");
            request.addHeader("Authorization", "Bearer good");
            HandlerMethod handler = handler(new PublicApi(), "open");
            assertSame(failure, assertThrows(RuntimeException.class,
                    () -> interceptor.preHandle(request, response, handler)));
            assertNull(SecurityContext.getSession());
        }
    }

    @Test
    void explicitRejectionAndWrittenErrorsAreNotChangedToSuccess() throws Exception {
        LoginInterceptor custom = mock(LoginInterceptor.class);
        interceptor = registered(custom);
        HandlerMethod handler = handler(new PublicApi(), "open");
        assertFalse(interceptor.preHandle(request, response, handler)); // 自定义返回 false。
        when(custom.preHandle(request, response, handler)).thenThrow(new LoginException("拒绝"));
        response.setStatus(403);
        assertThrows(ResponseStatusException.class, () -> interceptor.preHandle(request, response, handler));
        response.setStatus(200);
        response.flushBuffer();
        assertThrows(ResponseStatusException.class, () -> interceptor.preHandle(request, response, handler));
        assertNull(SecurityContext.getSession());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Bearer good", "Bearer expired", "Bearer rejected", "Basic bad"})
    void staticResourcesSkipTokenParsingAndClearPreviousIdentity(String authorization) throws Exception {
        if (authorization != null) request.addHeader("Authorization", authorization);
        for (Object handler : List.of(new ResourceHttpRequestHandler(), new DefaultServletHttpRequestHandler())) {
            SecurityContext.set(valid);
            assertTrue(interceptor.preHandle(request, response, handler));
            assertNull(SecurityContext.getSession());
            assertEquals(200, response.getStatus());
            interceptor.afterCompletion(request, response, handler, null);
        }
        verify(manager, never()).resolveSession(anyString());
    }

    @Test
    void staticResourcesSkipAllCustomLoginCallbacksButKeepContextCleanup() throws Exception {
        LoginInterceptor custom = mock(LoginInterceptor.class);
        interceptor = registered(custom); // mock 默认返回 false，若被调用就会阻止访问。
        for (Object handler : List.of(new ResourceHttpRequestHandler(), new DefaultServletHttpRequestHandler())) {
            SecurityContext.set(valid);
            assertTrue(interceptor.preHandle(request, response, handler));
            assertNull(SecurityContext.getSession());
            interceptor.postHandle(request, response, handler, null);

            SecurityContext.set(valid);
            interceptor.afterCompletion(request, response, handler, new IllegalStateException("资源读取失败"));
            assertNull(SecurityContext.getSession());

            assertTrue(interceptor.preHandle(request, response, handler));
            SecurityContext.set(valid);
            interceptor.afterConcurrentHandlingStarted(request, response, handler);
            assertNull(SecurityContext.getSession());
        }
        verifyNoInteractions(custom);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/static/private.js", "/assets/private.css", "/private.html", "/favicon.ico"})
    void resourceLikeControllerPathStillRequiresAuthentication(String path) throws Exception {
        request.setRequestURI(path);
        request.addHeader("Authorization", "Bearer expired");
        HandlerMethod handler = handler(new PublicApi(), "login");
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, handler)).getStatusCode());
        assertNull(SecurityContext.getSession());
    }

    @Test
    void unknownHandlerDoesNotReceiveAnonymousFallback() {
        request.addHeader("Authorization", "Bearer missing");
        assertThrows(ResponseStatusException.class, () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void optionsAndAsyncCleanupDoNotLeaveIdentityOnThread() throws Exception {
        HandlerMethod handler = handler(new PublicApi(), "open");
        request.setMethod("OPTIONS");
        request.addHeader("Authorization", "Bearer rejected");
        assertTrue(interceptor.preHandle(request, response, handler));
        verify(manager, never()).resolveSession(anyString());
        SecurityContext.set(valid);
        interceptor.afterConcurrentHandlingStarted(request, response, handler);
        assertNull(SecurityContext.getSession());
    }

    @Test
    void externalContextRemainsAnonymousOnAuthenticationFailureButCanLoginAgain() {
        ISecurityManager external = mock(ISecurityManager.class,
                withSettings().extraInterfaces(SecurityContextResolver.class));
        SecurityContextResolver resolver = (SecurityContextResolver) external;
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("securityManager", external);
        SecurityUtil binding = new SecurityUtil(beans.getBeanProvider(ISecurityManager.class));
        binding.afterSingletonsInstantiated();
        try {
            when(resolver.resolveContext(any())).thenThrow(new LoginException("过期"));
            SecurityContext.set(valid);
            assertNull(SecurityContext.getUser());
            assertNull(SecurityContext.getUser());
            assertThrows(LoginException.class, SecurityContext::requireUser);
            reset(external);
            when(resolver.resolveContext(any())).thenReturn(valid);
            assertSame(valid.user(), SecurityContext.getUser()); // 同请求内重新完成可信登录。
            reset(external);
            IllegalStateException failure = new IllegalStateException("身份存储不可用");
            when(resolver.resolveContext(any())).thenThrow(failure);
            assertSame(failure, assertThrows(IllegalStateException.class, SecurityContext::getUser));
        } finally {
            binding.destroy();
            SecurityContext.clear();
        }
    }

    private static HandlerMethod handler(Object bean, String name) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(name));
    }

    private static AsyncHandlerInterceptor registered(LoginInterceptor delegate) {
        class Registry extends InterceptorRegistry {
            List<Object> entries() { return getInterceptors(); }
        }
        Registry registry = new Registry();
        new LoginContextWebMvcConfigurer(delegate).addInterceptors(registry);
        return (AsyncHandlerInterceptor) registry.entries().getFirst();
    }

    @ApiPublic
    public static class PublicApi {
        public void open() { }
        @ApiLogin public void login() { }
        @ApiRole("admin") public void role() { }
        @ApiPerm("video:write") public void permission() { }
    }

    @ApiLogin
    public static class LoginApi {
        @ApiPublic public void open() { }
    }

    public static class UnannotatedApi { public void open() { } }
    public interface PublicContract { @ApiPublic void open(); }
    @ApiLogin
    public static class InterfaceApi implements PublicContract { @Override public void open() { } }
    public static class InheritedApi extends LoginApi { @Override public void open() { } }
    @ApiRole("admin")
    public static class ConflictingApi { @ApiPublic public void open() { } }
}

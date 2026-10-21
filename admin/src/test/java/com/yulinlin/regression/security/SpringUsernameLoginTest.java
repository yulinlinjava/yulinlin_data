package com.yulinlin.regression.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.SecurityLoginAutoConfiguration;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.ApiKeyLogin;
import com.yulinlin.security.auth.PasswordLogin;
import com.yulinlin.security.spring.LoginRequestAuthenticationToken;
import com.yulinlin.security.spring.LoginAuthenticatorAuthenticationProvider;
import com.yulinlin.security.auth.LoginAuthenticator;
import com.yulinlin.security.auth.UserIdLogin;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.spring.SpringLoginDetails;
import com.yulinlin.security.spring.SpringSecurityAdapterAutoConfiguration;
import com.yulinlin.security.spring.SpringUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static com.yulinlin.regression.security.LoginTestSupport.*;
import static org.mockito.Mockito.*;

/** 账号密码入口回归案例；仅位于 test 源码，不连接服务、数据库或读取 YAML。 */
@ResourceLock("SecurityUtil")
@ResourceLock("SpringSecurityContextHolder")
class SpringUsernameLoginTest {
    private Object previousBinding;

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                SpringSecurityAdapterAutoConfiguration.class,
                SecurityAuthAutoConfiguration.class, SecurityLoginAutoConfiguration.class))
                .withBean("passwordAuthenticator", LoginAuthenticator.class,
                        () -> authenticator(PasswordLogin.class, "password", ignored -> safeUser()))
                .withBean("apiKeyAuthenticator", LoginAuthenticator.class,
                        () -> authenticator(ApiKeyLogin.class, "api-key", ignored -> safeUser()));
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

    @Test void passwordLoginDelegatesRotatesSessionAndRestoresIdentityOnTheNextRequest() {
        AuthenticationManager authenticationManager = request -> {
            assertFalse(request.isAuthenticated());
            var token = assertInstanceOf(LoginRequestAuthenticationToken.class, request);
            var password = assertInstanceOf(PasswordLogin.class, token.getLoginRequest());
            assertEquals("alice", password.username());
            assertEquals(" password with spaces ", password.password()); // 不修改密码
            assertEquals("password", token.getAuthType());
            assertEquals("web", assertInstanceOf(SpringLoginDetails.class, request.getDetails()).getDeviceType());
            return authenticated();
        };
        runner().withBean(AuthenticationManager.class, () -> authenticationManager).run(context -> {
            MockHttpServletRequest request = request();
            MockHttpServletResponse response = response();
            String before = request.getSession().getId();
            request.setRequestedSessionId(before);
            request.setRequestedSessionIdValid(true);
            HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
            var oldCsrf = csrf.generateToken(request);
            csrf.saveToken(oldCsrf, request, response);

            LoginResult result = SecurityUtil.login(new PasswordLogin("alice", " password with spaces ", "web"));
            assertEquals("alice", result.user().getUserId());
            assertNull(result.token());
            assertNull(result.expiresAt());
            assertTrue(SecurityUtil.hasRole("ADMIN"));
            assertNotEquals(before, request.getSession().getId());
            assertNotEquals(oldCsrf, csrf.loadToken(request));
            Authentication saved = SecurityContextHolder.getContext().getAuthentication();
            assertNull(saved.getCredentials());
            assertNull(((User) saved.getPrincipal()).getPassword());
            assertEquals("web", ((SpringLoginDetails) saved.getDetails()).getDeviceType());
            assertEquals("password", ((SpringLoginDetails) saved.getDetails()).getAuthType());
            assertEquals("password", result.authType());
            assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("sensitive");

            MockHttpSession session = (MockHttpSession) request.getSession(false);
            SecurityContextHolder.clearContext();
            SecurityContext.clear();
            MockHttpServletRequest next = new MockHttpServletRequest("POST", "/protected");
            next.setSession(session);
            SecurityContextRepository repository = context.getBean(SecurityContextRepository.class);
            new SecurityContextHolderFilter(repository).doFilter(next, new MockHttpServletResponse(), (req, res) -> {
                assertEquals("alice", SecurityUtil.getUserId());
                assertTrue(SecurityUtil.hasPermission("video:edit"));
                assertEquals("password", SecurityUtil.getAuthType());
            });
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(SecurityContext.getUser());
        });
    }

    @Test void apiKeyLoginUsesAuthenticationManagerAndErasesTheKeyBeforeSaving() {
        AtomicReference<Authentication> submitted = new AtomicReference<>();
        AuthenticationManager manager = credentials -> {
            submitted.set(credentials);
            var token = assertInstanceOf(LoginRequestAuthenticationToken.class, credentials);
            assertFalse(credentials.isAuthenticated());
            assertEquals("partner-secret", assertInstanceOf(ApiKeyLogin.class, token.getLoginRequest()).apiKey());
            assertEquals("api-key", token.getAuthType());
            assertEquals("server", ((SpringLoginDetails) credentials.getDetails()).getDeviceType());
            return authenticated();
        };
        runner().withBean(AuthenticationManager.class, () -> manager).run(context -> {
            MockHttpServletRequest request = request();
            var result = SecurityUtil.login(new ApiKeyLogin("partner-secret", "server"));
            assertNull(result.token()); // 仍走 Spring Session，不生成第二套 Token
            assertEquals("alice", result.user().getUserId());
            assertEquals("api-key", result.authType());
            assertNull(submitted.get().getCredentials());
            assertNull(SecurityContextHolder.getContext().getAuthentication().getCredentials());
            SecurityContextHolder.clearContext();
            SecurityContext.clear();
            MockHttpServletRequest next = new MockHttpServletRequest("POST", "/protected");
            next.setSession((MockHttpSession) request.getSession(false));
            var repository = context.getBean(SecurityContextRepository.class);
            new SecurityContextHolderFilter(repository).doFilter(next, new MockHttpServletResponse(), (req, res) -> {
                assertEquals("alice", SecurityUtil.getUserId());
                assertTrue(SecurityUtil.hasPermission("video:edit"));
                assertEquals("api-key", SecurityUtil.getAuthType());
            });
        });
    }

    @Test void apiKeyNeedsAProviderAndCannotBeMarkedAuthenticatedByTheCaller() {
        var credentials = new LoginRequestAuthenticationToken(new ApiKeyLogin("secret"), "api-key");
        assertThrows(IllegalArgumentException.class, () -> credentials.setAuthenticated(true));
        assertFalse(credentials.isAuthenticated());
        assertFalse(credentials.toString().contains("secret"));
        credentials.eraseCredentials();
        assertNull(credentials.getCredentials());
        AtomicReference<Authentication> submitted = new AtomicReference<>();
        AuthenticationManager unsupported = input -> {
            submitted.set(input);
            throw new org.springframework.security.authentication.ProviderNotFoundException("未配置密钥认证提供者");
        };
        runner().withBean(AuthenticationManager.class, () -> unsupported).run(context -> {
            request();
            assertThrows(LoginException.class, () -> SecurityUtil.login(new ApiKeyLogin("key")));
            assertNull(submitted.get().getCredentials());
            assertNull(SecurityContext.getUser());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        });
    }

    @Test void staticAndInjectedOverloadsForwardTheDeviceAndUseDefaultWhenOmitted() {
        List<String> devices = new ArrayList<>();
        AuthenticationManager manager = request -> {
            devices.add(((SpringLoginDetails) request.getDetails()).getDeviceType());
            return authenticated();
        };
        runner().withBean(AuthenticationManager.class, () -> manager).run(context -> {
            request();
            SecurityUtil.login(new PasswordLogin("alice", "password"));
            SecurityUtil.login(new PasswordLogin("alice", "password", "ios"));
            context.getBean(ISecurityManager.class).login(new PasswordLogin("alice", "password"));
            context.getBean(ISecurityManager.class).login(new PasswordLogin("alice", "password", "android"));
            assertEquals(List.of("default", "ios", "default", "android"), devices);
        });
    }

    @Test void badPasswordClearsOldIdentityAndStoredContextAndErasesSubmittedPassword() {
        AtomicReference<Authentication> submitted = new AtomicReference<>();
        AuthenticationManager manager = authentication -> {
            submitted.set(authentication);
            throw new BadCredentialsException("模拟认证拒绝");
        };
        runner().withBean(AuthenticationManager.class, () -> manager).run(context -> {
            MockHttpServletRequest request = request();
            var old = SecurityContextHolder.createEmptyContext();
            old.setAuthentication(authenticated());
            SecurityContextHolder.setContext(old);
            SecurityContextRepository repository = context.getBean(SecurityContextRepository.class);
            repository.saveContext(old, request, response());

            assertThrows(LoginException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "wrong")));
            assertNull(submitted.get().getCredentials());
            assertNull(SecurityContext.getUser());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(request.getSession(false).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
            assertNull(repository.loadDeferredContext(request).get().getAuthentication());
        });
    }

    @Test void customStrategyAndRepositoryReplaceDefaultsAndRunAfterAuthentication() {
        List<String> calls = new ArrayList<>();
        AuthenticationManager manager = authentication -> { calls.add("authenticate"); return authenticated(); };
        SessionAuthenticationStrategy strategy = (authentication, request, response) -> calls.add("strategy");
        SecurityContextRepository repository = mock(SecurityContextRepository.class);
        doAnswer(call -> {
            calls.add("save");
            var saved = call.getArgument(0, org.springframework.security.core.context.SecurityContext.class);
            assertSame(saved, SecurityContextHolder.getContext());
            return null;
        }).when(repository).saveContext(any(), any(), any());
        runner().withBean(AuthenticationManager.class, () -> manager)
                .withBean(SessionAuthenticationStrategy.class, () -> strategy)
                .withBean(SecurityContextRepository.class, () -> repository).run(context -> {
                    assertThat(context).hasSingleBean(SessionAuthenticationStrategy.class)
                            .hasSingleBean(SecurityContextRepository.class);
                    request();
                    SecurityUtil.login(new PasswordLogin("alice", "password"));
                    assertEquals(List.of("authenticate", "strategy", "save"), calls);
                });
    }

    @Test void rejectedSessionStrategyCannotEstablishLogin() {
        SessionAuthenticationStrategy strategy = (authentication, request, response) -> {
            throw new SessionAuthenticationException("模拟会话数达到上限");
        };
        runner().withBean(AuthenticationManager.class, () -> authentication -> authenticated())
                .withBean(SessionAuthenticationStrategy.class, () -> strategy).run(context -> {
                    MockHttpServletRequest request = request();
                    assertThrows(LoginException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
                    assertNull(SecurityContext.getUser());
                    assertNull(request.getSession(false));
                });
    }

    @Test void failureAfterSavingContextRemovesPersistedIdentityAndPropagatesTheError() {
        var repository = spy(new HttpSessionSecurityContextRepository());
        doAnswer(call -> {
            call.callRealMethod();
            var saved = call.getArgument(0, org.springframework.security.core.context.SecurityContext.class);
            if (saved.getAuthentication() != null) throw new IllegalStateException("模拟保存后失败");
            return null;
        }).when(repository).saveContext(any(), any(), any());
        runner().withBean(AuthenticationManager.class, () -> authentication -> authenticated())
                .withBean(SecurityContextRepository.class, () -> repository).run(context -> {
                    MockHttpServletRequest request = request();
                    assertThrows(IllegalStateException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
                    assertNull(SecurityContext.getUser());
                    assertFalse(repository.containsContext(request));
                });
    }

    @Test void missingManagerIsAllowedForReadOnlyBridgeButCannotPretendToLogin() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            request();
            var error = assertThrows(IllegalStateException.class,
                    () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
            assertTrue(error.getMessage().contains("AuthenticationManager"));
        });
    }

    @Test void requestAndResponseAreRequiredBeforeCredentialsAreAuthenticated() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        runner().withBean(AuthenticationManager.class, () -> manager).run(context -> {
            assertThrows(IllegalStateException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
            request();
            response().flushBuffer();
            assertThrows(IllegalStateException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
            verifyNoInteractions(manager);
        });
    }

    @Test void anonymousResultNullMappingAndBlankDeviceCannotProduceLogin() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenReturn(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        runner().withBean(AuthenticationManager.class, () -> manager).run(context -> {
            request();
            assertThrows(LoginException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
            assertNull(SecurityContext.getUser());
            clearInvocations(manager);
            assertThrows(IllegalArgumentException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password", " ")));
            verifyNoInteractions(manager);
        });
        runner().withBean(AuthenticationManager.class, () -> authentication -> authenticated())
                .withBean(SpringUserMapper.class, () -> authentication -> null).run(context -> {
                    MockHttpServletRequest request = request();
                    assertThrows(LoginException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
                    assertNull(request.getSession(false));
                    assertNull(SecurityContext.getUser());
                });
    }

    @Test void protocolDefaultsDoNotAccidentallyTreatUsernameAsUserId() {
        ISecurityManager manager = new AnonymousSecurityManager();
        assertThrows(UnsupportedOperationException.class, () -> manager.login(new PasswordLogin("alice", "password")));
        assertThrows(UnsupportedOperationException.class, () -> manager.login(new PasswordLogin("alice", "password", "web")));
    }

    @Test void customAuthenticationDetailsArePreservedButUnclearableCredentialsAreRejected() {
        Object providerDetails = new Object();
        AuthenticationManager manager = authentication -> {
            var result = (UsernamePasswordAuthenticationToken) authenticated();
            result.setDetails(providerDetails);
            return result;
        };
        runner().withBean(AuthenticationManager.class, () -> manager).run(context -> {
            request();
            SecurityUtil.login(new PasswordLogin("alice", "password", "web"));
            var saved = assertInstanceOf(SpringLoginDetails.class,
                    SecurityContextHolder.getContext().getAuthentication().getDetails());
            assertSame(providerDetails, saved.getProviderDetails());
            assertEquals("password", saved.getAuthType());
        });

        Authentication unsafe = mock(Authentication.class);
        when(unsafe.isAuthenticated()).thenReturn(true);
        when(unsafe.getCredentials()).thenReturn("sensitive-password");
        runner().withBean(AuthenticationManager.class, () -> authentication -> unsafe).run(context -> {
            MockHttpServletRequest request = request();
            assertThrows(IllegalStateException.class, () -> SecurityUtil.login(new PasswordLogin("alice", "password")));
            assertNull(SecurityContext.getUser());
            assertNull(request.getSession(false));
        });
    }

    @Test void realBridgeProviderSupportsAllRegisteredTypesIncludingTrustedUserId() {
        runner().withUserConfiguration(BridgeConfiguration.class)
                .withBean(UserStore.class, () -> id -> "safe-id".equals(id) ? safeUser() : null)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    request();
                    var password = SecurityUtil.login(new PasswordLogin("alice", "password", "web"));
                    assertEquals("safe-id", password.user().getUserId());
                    assertEquals("password", password.authType());
                    assertEquals("password", SecurityContext.getAuthType());
                    assertTrue(SecurityUtil.hasRole("ADMIN"));
                    var apiKey = SecurityUtil.login(new ApiKeyLogin("verified-key", "server"));
                    assertEquals("api-key", apiKey.authType());
                    var trusted = SecurityUtil.login(new UserIdLogin("safe-id", "internal"));
                    assertEquals("user-id", trusted.authType());
                    assertEquals("user-id", SecurityUtil.getAuthType());
                    assertThrows(LoginException.class, () -> SecurityUtil.login(new UserIdLogin("missing")));
                    assertNull(SecurityContext.getUser());
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class BridgeConfiguration {
        @org.springframework.context.annotation.Bean
        AuthenticationManager authenticationManager(LoginAuthenticatorAuthenticationProvider provider) {
            return new org.springframework.security.authentication.ProviderManager(List.of(provider));
        }
    }

    private static com.yulinlin.security.auth.IUserDetails safeUser() {
        return new com.yulinlin.security.spring.DefaultSpringUserMapper.UserSnapshot(
                "safe-id", "alice", java.util.Set.of("ADMIN"), java.util.Set.of("video:edit"));
    }

    private static Authentication authenticated() {
        var principal = User.withUsername("alice").password("sensitive-password")
                .authorities("ROLE_ADMIN", "video:edit").build();
        return UsernamePasswordAuthenticationToken.authenticated(principal, "sensitive-credential", principal.getAuthorities());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        return request;
    }

    private static MockHttpServletResponse response() {
        return (MockHttpServletResponse) ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    }
}

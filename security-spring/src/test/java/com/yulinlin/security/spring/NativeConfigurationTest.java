package com.yulinlin.security.spring;

import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.session.LoginSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.SessionManagementFilter;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeConfigurationTest {
    @FunctionalInterface interface Dsl { void apply(HttpSecurity http) throws Exception; }
    record Customization(Dsl before, Dsl after) { }

    private WebApplicationContextRunner runner(Dsl before, Dsl after) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAuthAutoConfiguration.class,
                        SpringSecurityAdapterAutoConfiguration.class))
                .withUserConfiguration(WebSecurity.class)
                .withBean(Customization.class, () -> new Customization(before, after));
    }

    @Test void nativeCsrfFixationAndUnifiedLimitWorkTogether() {
        runner(http -> http.sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId())), http -> { })
                .withPropertyValues("yulinlin.security.session.max-sessions=1").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecurityFilterChain.class).getFilters())
                            .anyMatch(SessionManagementFilter.class::isInstance);
                    var support = context.getBean(SpringSessionSupport.class);
                    var registry = context.getBean(SessionRegistry.class);
                    var auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
                    var request = new MockHttpServletRequest();
                    String oldId = request.getSession().getId();
                    support.onAuthentication(auth, request, new MockHttpServletResponse());
                    String first = request.getSession().getId();
                    assertThat(first).isNotEqualTo(oldId);
                    assertThat(registry.getSessionInformation(first)).isNotNull();
                    support.onAuthentication(auth, new MockHttpServletRequest(), new MockHttpServletResponse());
                    assertThat(registry.getSessionInformation(first).isExpired()).isTrue();
                    var csrf = context.getBean(SecurityFilterChain.class).getFilters().stream()
                            .filter(CsrfFilter.class::isInstance).map(CsrfFilter.class::cast).findFirst().orElseThrow();
                    var post = new MockHttpServletRequest("POST", "/update");
                    var response = new MockHttpServletResponse();
                    csrf.doFilter(post, response, new MockFilterChain());
                    assertThat(response.getStatus()).isEqualTo(403);
                });
    }

    @Test void nativeRejectNewLoginPolicyIsPreserved() {
        runner(http -> http.sessionManagement(s -> s.sessionConcurrency(c -> c.maxSessionsPreventsLogin(true))), http -> { })
                .withPropertyValues("yulinlin.security.session.max-sessions=1").run(context -> {
                    assertThat(context).hasNotFailed();
                    var support = context.getBean(SpringSessionSupport.class);
                    var auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
                    support.onAuthentication(auth, new MockHttpServletRequest(), new MockHttpServletResponse());
                    assertThatThrownBy(() -> support.onAuthentication(auth, new MockHttpServletRequest(), new MockHttpServletResponse()))
                            .isInstanceOf(SessionAuthenticationException.class);
                });
    }

    @Test void defaultLimitDoesNotRestrictConcurrentLogins() {
        runner(http -> { }, http -> { }).run(context -> {
            assertThat(context).hasNotFailed();
            var support = context.getBean(SpringSessionSupport.class);
            var auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
            for (int i = 0; i < 4; i++) {
                support.onAuthentication(auth, new MockHttpServletRequest(), new MockHttpServletResponse());
            }
            assertThat(context.getBean(SessionRegistry.class).getAllSessions("user", false)).hasSize(4);
        });
    }

    @Test void customCsrfRepositoryAndHeaderRemainEffective() {
        var csrfRepository = new HttpSessionCsrfTokenRepository();
        csrfRepository.setHeaderName("X-CUSTOM-CSRF");
        runner(http -> http.csrf(c -> c.csrfTokenRepository(csrfRepository)), http -> { }).run(context -> {
            assertThat(context).hasNotFailed();
            var filter = context.getBean(SecurityFilterChain.class).getFilters().stream()
                    .filter(CsrfFilter.class::isInstance).map(CsrfFilter.class::cast).findFirst().orElseThrow();
            var get = new MockHttpServletRequest("GET", "/auth/csrf");
            filter.doFilter(get, new MockHttpServletResponse(), new MockFilterChain());
            var token = (CsrfToken) get.getAttribute(CsrfToken.class.getName());
            assertThat(token.getHeaderName()).isEqualTo("X-CUSTOM-CSRF");
            String value = token.getToken();
            var post = new MockHttpServletRequest("POST", "/update");
            post.setSession(get.getSession());
            post.addHeader(token.getHeaderName(), value);
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(post, response, chain);
            assertThat(chain.getRequest()).isSameAs(post);
            assertThat(response.getStatus()).isEqualTo(200);
        });
    }

    @Test void nativeFixationStrategyCanBeCustomized() {
        runner(http -> http.sessionManagement(s -> s.sessionFixation(f -> f.newSession())), http -> { }).run(context -> {
            assertThat(context).hasNotFailed();
            var request = new MockHttpServletRequest();
            request.getSession().setAttribute("pre-login-data", "discard-me");
            context.getBean(SpringSessionSupport.class).onAuthentication(
                    new UsernamePasswordAuthenticationToken("user", null, List.of()), request, new MockHttpServletResponse());
            assertThat(request.getSession().getAttribute("pre-login-data")).isNull();
        });
    }

    @Test void nativeExpiredRedirectIsPreserved() {
        runner(http -> http.sessionManagement(s -> s.sessionConcurrency(c -> c.expiredUrl("/session-expired"))), http -> { })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var request = new MockHttpServletRequest();
                    String id = request.getSession().getId();
                    var registry = context.getBean(SessionRegistry.class);
                    registry.registerNewSession(id, "user");
                    registry.getSessionInformation(id).expireNow();
                    var filter = context.getBean(SecurityFilterChain.class).getFilters().stream()
                            .filter(ConcurrentSessionFilter.class::isInstance).map(ConcurrentSessionFilter.class::cast)
                            .findFirst().orElseThrow();
                    var response = new MockHttpServletResponse();
                    filter.doFilter(request, response, new MockFilterChain());
                    assertThat(response.getRedirectedUrl()).isEqualTo("/session-expired");
                });
    }

    @Test void customExpiredResponseIsPreserved() {
        runner(http -> http.sessionManagement(s -> s.sessionConcurrency(c -> c
                .expiredSessionStrategy(event -> event.getResponse().setStatus(418)))), http -> { })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var request = new MockHttpServletRequest();
                    String id = request.getSession().getId();
                    var registry = context.getBean(SessionRegistry.class);
                    registry.registerNewSession(id, "user");
                    registry.getSessionInformation(id).expireNow();
                    var filter = context.getBean(SecurityFilterChain.class).getFilters().stream()
                            .filter(ConcurrentSessionFilter.class::isInstance).map(ConcurrentSessionFilter.class::cast)
                            .findFirst().orElseThrow();
                    var response = new MockHttpServletResponse();
                    filter.doFilter(request, response, new MockFilterChain());
                    assertThat(response.getStatus()).isEqualTo(418);
                });
    }

    @Test void customRegistryAndContextRepositoryBeansAreReused() {
        var registry = new SessionRegistryImpl();
        var repository = new HttpSessionSecurityContextRepository();
        runner(http -> {
            http.securityContext(c -> c.securityContextRepository(repository));
            http.sessionManagement(s -> s.sessionConcurrency(c -> c.sessionRegistry(registry)));
        }, http -> { }).withBean(SessionRegistry.class, () -> registry)
                .withBean(SecurityContextRepository.class, () -> repository).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SessionRegistry.class)).isSameAs(registry);
                    assertThat(context.getBean(SecurityContextRepository.class)).isSameAs(repository);
                });
    }

    static Stream<Dsl> conflictingConfigurations() {
        return Stream.of(
                http -> http.sessionManagement(s -> s.maximumSessions(3)),
                http -> http.sessionManagement(s -> s.maximumSessions(5)),
                http -> http.sessionManagement(s -> s.sessionConcurrency(c -> c.maximumSessions(authentication -> 5))),
                http -> http.sessionManagement(s -> s.sessionConcurrency(c -> c.sessionRegistry(new SessionRegistryImpl()))),
                http -> http.securityContext(c -> c.securityContextRepository(new HttpSessionSecurityContextRepository())),
                http -> http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)),
                http -> http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.NEVER)));
    }

    @ParameterizedTest @MethodSource("conflictingConfigurations")
    void conflictingNativeConfigurationFailsBeforeBeingOverwritten(Dsl conflict) {
        runner(conflict, http -> { }).withPropertyValues("yulinlin.security.session.max-sessions=3")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("统一会话策略冲突"));
    }

    @ParameterizedTest @MethodSource("conflictingConfigurations")
    void changesAfterIntegrationAlsoFailAtStartup(Dsl conflict) {
        runner(http -> { }, conflict).withPropertyValues("yulinlin.security.session.max-sessions=3")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("统一会话策略冲突"));
    }

    @Test void disablingNativeSessionConfigurerAfterIntegrationFails() {
        runner(http -> { }, http -> http.sessionManagement(s -> s.disable()))
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("SessionManagementConfigurer"));
    }

    @Test void disablingContextAfterIntegrationFails() {
        runner(http -> { }, http -> http.securityContext(s -> s.disable()))
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("SecurityContext"));
    }

    @Test void disabledManagementDoesNotOwnNativeLimit() {
        runner(http -> http.sessionManagement(s -> s.maximumSessions(5)), http -> { })
                .withPropertyValues("yulinlin.security.session.enabled=false", "yulinlin.security.session.max-sessions=3")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SpringSessionSupport.class)
                        .doesNotHaveBean(LoginSessionManager.class).hasSingleBean(SpringSecurityManager.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurity {
        @Bean AuthenticationManager authenticationManager() { return authentication -> authentication; }

        @Bean SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<SpringSessionSupport> support,
                                               Customization customization) throws Exception {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            http.csrf(Customizer.withDefaults());
            customization.before().apply(http);
            if (support.getIfAvailable() != null) support.getObject().configure(http);
            customization.after().apply(http);
            return http.build();
        }
    }
}

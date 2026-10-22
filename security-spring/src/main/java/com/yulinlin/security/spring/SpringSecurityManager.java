package com.yulinlin.security.spring;

import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.LoginRequest;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityContextResolver;
import com.yulinlin.security.auth.UserStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.function.Supplier;

/** 适配 Spring 身份；账号密码、密钥都委托 AuthenticationManager，会话策略和身份持久化复用 Spring 协议。 */
public final class SpringSecurityManager implements ISecurityManager, SecurityContextResolver {
    @Override public boolean sessionManagementEnabledByDefault() { return true; }

    private final SpringUserMapper mapper;
    private final Supplier<UserStore> users;
    private final Supplier<AuthenticationManager> authenticationManagers;
    private final Supplier<SecurityContextRepository> repositories;
    private final Supplier<SessionAuthenticationStrategy> sessionStrategies;
    private final LoginAuthenticatorRegistry authenticators;
    private final AuthenticationTrustResolverImpl trust = new AuthenticationTrustResolverImpl();

    /** 仅桥接已有身份；手动调用账号密码登录时使用带认证、存储和会话策略的构造器。 */
    public SpringSecurityManager(SpringUserMapper mapper, Supplier<UserStore> users) {
        this(mapper, users, new LoginAuthenticatorRegistry(java.util.List.of(), null), () -> null, () -> null, () -> null);
    }

    public SpringSecurityManager(SpringUserMapper mapper, Supplier<UserStore> users,
                                 LoginAuthenticatorRegistry authenticators,
                                 Supplier<AuthenticationManager> authenticationManagers,
                                 Supplier<SecurityContextRepository> repositories,
                                 Supplier<SessionAuthenticationStrategy> sessionStrategies) {
        this.mapper = Objects.requireNonNull(mapper, "用户映射器不能为空");
        this.users = Objects.requireNonNull(users, "用户加载器提供者不能为空");
        this.authenticators = Objects.requireNonNull(authenticators, "认证器注册表不能为空");
        this.authenticationManagers = Objects.requireNonNull(authenticationManagers, "认证管理器提供者不能为空");
        this.repositories = Objects.requireNonNull(repositories, "安全上下文存储提供者不能为空");
        this.sessionStrategies = Objects.requireNonNull(sessionStrategies, "会话认证策略提供者不能为空");
    }

    /** 原生过滤器可能在 MVC 前直接结束请求，因此不在框架 ThreadLocal 中重复保留身份。 */
    @Override public boolean cacheContext() { return false; }

    @Override
    public LoginSession resolveContext(LoginSession cached) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || trust.isAnonymous(authentication)) {
            return null;
        }
        // 不沿用 cached：同一个 Authentication 也可能被业务原地改权限或切换 principal。
        IUserDetails user = mapper.map(authentication);
        if (user == null) return null;
        requireUserId(user.getUserId());
        // Session、Basic、OAuth2 都不保证存在可返回的 Token；绝不能把 credentials 当作 Token。
        String authType = authentication.getDetails() instanceof SpringLoginDetails details ? details.getAuthType() : null;
        return new LoginSession(null, null, user, authType);
    }

    @Override
    public LoginResult login(LoginRequest login) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                || attributes.getResponse() == null) {
            throw new IllegalStateException("Spring 登录需要当前 Servlet 请求和响应，请在 MVC 接口中调用");
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response.isCommitted()) throw new IllegalStateException("响应已提交，无法保存登录状态，请在输出响应之前登录");
        AuthenticationManager manager = authenticationManagers.get();
        if (manager == null) throw new IllegalStateException("请将现有 Spring AuthenticationManager 注册为 Bean 后使用 login(LoginRequest)");
        SecurityContextRepository repository = repositories.get();
        SessionAuthenticationStrategy strategy = sessionStrategies.get();
        if (repository == null || strategy == null) {
            throw new IllegalStateException("Spring 登录需要 SecurityContextRepository 和 SessionAuthenticationStrategy");
        }

        AbstractAuthenticationToken credentials = null;
        SecurityContext.clear();
        SecurityContextHolder.clearContext();
        try {
            var registered = authenticators.required(login);
            String authType = registered.name();
            String deviceType = login.deviceType();
            credentials = new LoginRequestAuthenticationToken(login, authType);
            // 不修改原始凭证；设备只是元数据，不能作为认证凭证。
            credentials.setDetails(new SpringLoginDetails(request, deviceType, authType));
            Authentication authenticated = manager.authenticate(credentials);
            if (authenticated instanceof CredentialsContainer sensitive) sensitive.eraseCredentials();
            if (authenticated == null || !authenticated.isAuthenticated() || trust.isAnonymous(authenticated)) {
                throw new LoginException("登录凭证无效或认证未通过");
            }
            if (authenticated.getCredentials() != null) {
                throw new IllegalStateException("认证结果仍含凭证，请让 AuthenticationManager 返回可清除凭证的 Authentication，不能将密码或密钥保存到会话");
            }
            if (!(authenticated instanceof AbstractAuthenticationToken token)) {
                throw new IllegalStateException("统一登录的认证结果必须支持保存认证详情，请返回 AbstractAuthenticationToken 实现");
            }
            Object providerDetails = token.getDetails();
            if (providerDetails instanceof SpringLoginDetails details) providerDetails = details.getProviderDetails();
            token.setDetails(new SpringLoginDetails(request, deviceType, authType, providerDetails));
            IUserDetails user = mapper.map(authenticated);
            if (user == null) throw new LoginException("当前账号不能登录");
            requireUserId(user.getUserId());
            strategy.onAuthentication(authenticated, request, response);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            // 仅 setContext 不能保证下个请求仍已登录，必须交给同一套 Spring 存储协议。
            repository.saveContext(context, request, response);
            return new LoginResult(null, null, user, authType);
        } catch (AuthenticationException error) {
            clearFailedSession(strategy, request, error);
            clearFailedLogin(repository, request, response, error);
            throw new LoginException("登录认证或会话校验未通过，请检查凭证、认证提供者与登录限制", error);
        } catch (RuntimeException | Error error) {
            clearFailedSession(strategy, request, error);
            clearFailedLogin(repository, request, response, error);
            throw error;
        } finally {
            if (credentials != null) credentials.eraseCredentials();
        }
    }

    private static void clearFailedSession(SessionAuthenticationStrategy strategy, HttpServletRequest request, Throwable failure) {
        if (strategy instanceof SpringSessionSupport support) {
            try { support.loginFailed(request); }
            catch (RuntimeException cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }
    }

    /** 认证、映射或保存失败后不保留本次/旧的线程身份，尽力清理存储；清理失败不覆盖原始异常。 */
    private static void clearFailedLogin(SecurityContextRepository repository, HttpServletRequest request,
                                         HttpServletResponse response, Throwable failure) {
        SecurityContext.clear();
        SecurityContextHolder.clearContext();
        try {
            repository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        } catch (RuntimeException | Error cleanup) {
            if (cleanup != failure) failure.addSuppressed(cleanup);
        }
    }

    @Override
    public void logout() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                || !springRequest(attributes.getRequest())) {
            throw new IllegalStateException("当前请求未启用 Spring Security Servlet API 集成，请使用原有注销入口，不能仅清理线程冒充注销");
        }
        try {
            // 使用当前过滤器链安装的 LogoutHandler（包括会话、Remember-Me 等），不另造注销链。
            attributes.getRequest().logout();
            Authentication remaining = SecurityContextHolder.getContext().getAuthentication();
            if (remaining != null && remaining.isAuthenticated() && !trust.isAnonymous(remaining)) {
                throw new IllegalStateException("Spring 原生注销没有清理当前认证，请检查 Servlet API/LogoutHandler 配置或使用原有注销入口");
            }
        } catch (ServletException error) {
            throw new LoginException("Spring Security 注销失败，请检查原生注销配置", error);
        } finally {
            SecurityContext.clear();
        }
    }

    @Override
    public IUserDetails getUserDetails(String userId) {
        requireUserId(userId);
        UserStore store = users.get();
        if (store != null) {
            IUserDetails user = store.load(userId);
            if (user != null && !userId.equals(user.getUserId())) {
                throw new IllegalStateException("UserStore 返回的用户 ID 与查询 ID 不一致");
            }
            return user;
        }
        IUserDetails current = getUserDetails();
        if (current != null && userId.equals(current.getUserId())) return current;
        throw new UnsupportedOperationException("查询其他用户需要提供 UserStore Bean；当前 Spring 身份不能代替用户目录");
    }

    @Override
    public IUserDetails refresh() {
        SecurityContext.clear();
        return getUserDetails();
    }

    @Override
    public IUserDetails refresh(String userId) {
        requireUserId(userId);
        IUserDetails current = getUserDetails();
        return current != null && userId.equals(current.getUserId()) ? refresh() : getUserDetails(userId);
    }

    private static void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("安全用户 ID 不能为空，请配置 SpringUserMapper");
    }

    private static boolean springRequest(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper) {
            if (current instanceof SecurityContextHolderAwareRequestWrapper) return true;
            current = wrapper.getRequest();
        }
        return false;
    }
}

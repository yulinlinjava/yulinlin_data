package com.yulinlin.security.auth;

/** 统一登录协议；按参数类型认证，UserIdLogin 只供已完成上游身份验证的可信服务端调用。 */
public interface ISecurityManager {

    /**
     * 未配置 session.enabled 时是否默认启用会话管理。Sa-Token/Spring 为 true，JWT/未知实现为 false。
     * 返回 true 的自定义实现还必须提供 LoginSessionManager 并接入真实登录/鉴权链路；显式开关优先。
     */
    default boolean sessionManagementEnabledByDefault() { return false; }

    /**
     * 统一登录入口，内置参数为 PasswordLogin / ApiKeyLogin / UserIdLogin，支持业务自定义参数。
     * JWT、Sa-Token 委托 LoginAuthenticator；Spring 经 AuthenticationManager 调用认证桥接 Provider。
     * 验证码由业务先校验；不支持的类型或未配置的认证方式必须拒绝，不能降级放行。
     * Session 模式的 LoginResult.token / expiresAt 可以为 null。
     */
    default LoginResult login(LoginRequest request) {
        throw new UnsupportedOperationException("当前安全管理器不支持登录，请引入登录模块或提供认证实现");
    }

    /** 验证 Token 并恢复请求快照；可无状态验签或校验服务端会话，不依赖当前上下文。 */
    default LoginSession resolveSession(String token) { return null; }

    /**
     * 结束当前登录：无状态 JWT 仅清理线程身份，有状态实现按自身协议撤销会话。
     * 使用内置上下文的实现必须清理 SecurityContext；客户端仍需删除本地 Token。
     */
    void logout();

    /**
     * Returns the user associated with the current request.
     *
     * @return current authenticated user, or {@code null} when this request is anonymous
     */
    default IUserDetails getUserDetails() { return SecurityContext.getUser(); }

    /** 当前请求的 Token；Session/Basic 等外部认证可在已登录时返回 null，不得把密码或 Session ID 冒充 Token。 */
    default String getToken() { return SecurityContext.getToken(); }

    /** 本次已验证登录的认证器名称；匿名或原生外部身份未提供该信息时为 null。 */
    default String getAuthType() { return SecurityContext.getAuthType(); }

    default boolean isLogin() { return getUserDetails() != null; }

    default IUserDetails requireUser() {
        IUserDetails user = getUserDetails();
        if (user == null) throw new LoginException("请先登录或重新登录");
        return user;
    }

    /**
     * Returns one user's details without changing the current request's identity.
     * Implementations may use an existing login snapshot or cache.
     *
     * @param userId user to read
     * @return user details, or {@code null} when the user does not exist
     */
    IUserDetails getUserDetails(String userId);

    /**
     * Forces the current authenticated user's details to be reloaded.
     *
     * @return refreshed authenticated user, or {@code null} when this request is anonymous
     */
    default IUserDetails refresh() {
        IUserDetails current = getUserDetails();
        return current == null ? null : refresh(current.getUserId());
    }

    /**
     * 重新加载用户；若为当前用户则更新当前请求快照，不切换身份、不延长 Token 有效期。
     *
     * @param userId user to reload
     * @return refreshed user details
     */
    IUserDetails refresh(String userId);
}

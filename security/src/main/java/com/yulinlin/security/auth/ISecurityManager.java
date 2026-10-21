package com.yulinlin.security.auth;

/** 登录协议；统一使用账号密码入口，具体实现负责认证，不开放仅凭 userId 建立登录的入口。 */
public interface ISecurityManager {

    /** 校验账号密码后建立登录；未指定设备时使用 default。 */
    default LoginResult loginByUsername(String username, String password) {
        return loginByUsername(username, password, "default");
    }

    /**
     * 委托具体实现校验账号密码及账号状态，然后建立登录。验证码仍由业务校验。
     * Spring 适配使用 AuthenticationManager；JWT、Sa-Token 使用业务提供的 UserAuthenticator。
     * 设备类型仅为登录元数据，不是身份凭证，也不自动代表设备隔离策略。
     *
     * @return 安全用户与登录凭证；Session 模式的 token、expiresAt 可以为 null
     * @throws LoginException 账号密码认证被拒绝或无法建立登录
     * @throws UnsupportedOperationException 当前实现未提供账号密码登录
     */
    default LoginResult loginByUsername(String username, String password, String deviceType) {
        throw new UnsupportedOperationException("当前安全管理器不支持账号密码登录，请引入登录模块或提供账号认证实现");
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

package com.yulinlin.security.auth;

/** 登录状态协议；账号、密码、验证码和账号状态由业务验证，禁止直接信任客户端提交的 userId。 */
public interface ISecurityManager {

    /** 为已经通过业务验证的用户建立登录，未指定设备时使用 default。 */
    default LoginResult login(String userId) {
        return login(userId, "default");
    }

    /**
     * 为已经通过业务验证的用户建立登录，不验证密码或验证码。
     * 使用内置上下文时，登录成功必须设置 SecurityContext，失败不能保留旧身份；
     * 外部安全框架适配可提供自定义 LoginInterceptor Bean，配合使用其自身的上下文。
     *
     * @param userId 业务验证得到的用户 ID，不是客户端未经验证的身份声明
     * @param deviceType 设备类型，例如 web、android、ios；设备类型不是认证凭证
     * @return Token、有效期和不含敏感凭证的用户信息；不可为 null
     * @throws LoginException 用户不存在或不能建立登录
     * @throws IllegalStateException when only the default anonymous manager is installed
     */
    LoginResult login(String userId, String deviceType);

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

    /** 当前请求的 Token；外部安全框架适配应按其自身上下文覆盖。 */
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

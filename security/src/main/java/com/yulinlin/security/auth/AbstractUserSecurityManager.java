package com.yulinlin.security.auth;

import java.util.Objects;

/** JWT / Sa-Token 共用凭证分发、业务认证、用户加载与请求快照刷新，具体实现负责签发或存储。 */
public abstract class AbstractUserSecurityManager implements ISecurityManager {
    private final UserStore users;
    private final LoginAuthenticatorRegistry authenticators;

    protected AbstractUserSecurityManager(UserStore users, LoginAuthenticatorRegistry authenticators) {
        this.users = Objects.requireNonNull(users, "UserStore 不能为空");
        this.authenticators = Objects.requireNonNull(authenticators, "认证器注册表不能为空");
    }

    @Override
    public final LoginResult login(LoginRequest request) {
        SecurityContext.clear();
        try {
            var authenticator = authenticators.required(request);
            String deviceType = request.deviceType();
            // 名称取自启动时注册的服务端认证器，不接受客户端声称的 authType。
            IUserDetails user = authenticator.authenticate(request);
            return createLogin(user, deviceType, authenticator.name());
        } catch (RuntimeException | Error failure) {
            SecurityContext.clear();
            throw failure;
        }
    }

    /** 仅供已验证凭证的内部流程调用，不暴露为业务登录 API。 */
    protected abstract LoginResult createLogin(IUserDetails user, String deviceType, String authType);

    @Override
    public IUserDetails getUserDetails(String userId) {
        requireText(userId, "用户 ID");
        IUserDetails user = users.load(userId);
        if (user != null && !userId.equals(user.getUserId())) {
            throw new IllegalStateException("UserStore 返回的用户 ID 与查询 ID 不一致");
        }
        return user;
    }

    protected final IUserDetails loadRequiredUser(String userId) {
        IUserDetails user = getUserDetails(userId);
        if (user == null) throw new LoginException("用户不存在或不允许登录");
        return user;
    }

    @Override
    public IUserDetails refresh(String userId) {
        requireText(userId, "用户 ID");
        LoginSession current = SecurityContext.getSession();
        if (current == null || !userId.equals(current.user().getUserId())) {
            return getUserDetails(userId);
        }
        // 先移除旧权限快照；加载失败不能继续使用旧身份，凭证有效性由具体实现重新检查。
        SecurityContext.clear();
        LoginSession refreshed = resolveSession(current.token());
        if (refreshed == null) return null;
        if (!userId.equals(refreshed.user().getUserId())) {
            throw new IllegalStateException("刷新登录信息时用户 ID 发生变化");
        }
        SecurityContext.set(refreshed);
        return refreshed.user();
    }

    protected static String requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(name + "不能为空或包含首尾空白");
        }
        return value;
    }
}

package com.yulinlin.security.auth;

import java.util.Objects;

/** JWT / Sa-Token 共用业务账号认证、用户加载与请求快照刷新，具体实现负责签发或存储。 */
public abstract class AbstractUserSecurityManager implements ISecurityManager {
    private final UserStore users;
    private final UserAuthenticator authenticator;

    protected AbstractUserSecurityManager(UserStore users) {
        this(users, null);
    }

    protected AbstractUserSecurityManager(UserStore users, UserAuthenticator authenticator) {
        this.users = Objects.requireNonNull(users, "UserStore 不能为空");
        this.authenticator = authenticator;
    }

    @Override
    public final LoginResult loginByUsername(String username, String password, String deviceType) {
        SecurityContext.clear();
        try {
            requireText(deviceType, "设备类型");
            if (username == null || username.isBlank() || password == null || password.isEmpty()) {
                throw new LoginException("用户名或密码错误");
            }
            if (authenticator == null) {
                throw new IllegalStateException("账号密码登录需要提供 UserAuthenticator Bean；UserStore 只加载用户，不校验密码");
            }
            // 保留原始密码，不 trim；不能把用户名当作用户 ID，也不能在缺少认证组件时直接签发。
            IUserDetails user = authenticator.authenticate(username, password);
            if (user == null) throw new LoginException("用户名或密码错误");
            requireText(user.getUserId(), "认证用户 ID");
            return createLogin(user, deviceType);
        } catch (RuntimeException | Error failure) {
            SecurityContext.clear();
            throw failure;
        }
    }

    /** 仅供已通过账号认证的内部流程调用，不暴露为业务登录 API。 */
    protected abstract LoginResult createLogin(IUserDetails user, String deviceType);

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

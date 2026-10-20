package com.yulinlin.security.auth;

import java.util.Objects;

/** 两种内置登录实现共用用户加载与请求快照刷新，不参与 Token 的签发或存储。 */
public abstract class AbstractUserSecurityManager implements ISecurityManager {
    private final UserStore users;

    protected AbstractUserSecurityManager(UserStore users) {
        this.users = Objects.requireNonNull(users, "UserStore 不能为空");
    }

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

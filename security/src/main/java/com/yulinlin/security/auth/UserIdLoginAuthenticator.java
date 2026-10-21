package com.yulinlin.security.auth;

import java.util.Objects;

/** 默认可信 ID 登录：只检查用户可用性，不承担上游身份凭证验证。 */
public final class UserIdLoginAuthenticator implements LoginAuthenticator<UserIdLogin> {
    private final UserStore users;

    public UserIdLoginAuthenticator(UserStore users) {
        this.users = Objects.requireNonNull(users, "UserStore 不能为空");
    }

    @Override public String name() { return "user-id"; }
    @Override public Class<UserIdLogin> requestType() { return UserIdLogin.class; }

    @Override
    public IUserDetails authenticate(UserIdLogin request) {
        IUserDetails user = users.load(request.userId());
        if (user != null && !request.userId().equals(user.getUserId())) {
            throw new IllegalStateException("UserStore 返回的用户 ID 与查询 ID 不一致");
        }
        return user;
    }
}

package com.yulinlin.admin.rbac.auth;

import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.SecurityContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 只做业务凭证校验；JWT 签发和登录记录交给 security-jwt。 */
@Service
public class AdminAuthenticationService {
    private final UserRepository users;
    private final PasswordHash passwords;
    private final String dummyHash;

    public AdminAuthenticationService(UserRepository users, PasswordHash passwords) {
        this.users = users;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public String authenticate(String username, String password, String code) {
        SecurityContext.clear();
        // 验证码属于业务，本案例未接入验证码服务，不假装已完成校验。
        if (code != null && !code.isBlank()) throw new LoginException("本案例未配置验证码校验");
        UserEntity user = username == null || username.isBlank() ? null : users.findByUsernameEq(username);
        boolean valid = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !valid) throw new LoginException("用户名或密码错误");
        if (user.getStatus() != UserStatus.ENABLED) throw new LoginException("账号已停用或锁定");
        return user.getId();
    }
}

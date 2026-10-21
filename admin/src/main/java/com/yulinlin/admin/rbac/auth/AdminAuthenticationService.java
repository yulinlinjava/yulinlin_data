package com.yulinlin.admin.rbac.auth;

import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.UserAuthenticator;
import com.yulinlin.security.auth.UserStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 一个业务组件提供账号认证和按 ID 加载用户；不保存密码到上下文，不签发 Token 或维护登录记录。 */
@Service
public class AdminAuthenticationService implements UserAuthenticator, UserStore {
    private final UserRepository users;
    private final PasswordHash passwords;
    private final String dummyHash;

    public AdminAuthenticationService(UserRepository users, PasswordHash passwords) {
        this.users = users;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public void validateCode(String code) {
        SecurityContext.clear();
        // 验证码属于业务，本案例未接入验证码服务，不假装已完成校验。
        if (code != null && !code.isBlank()) throw new LoginException("本案例未配置验证码校验");
    }

    @Override
    public AdminUserDetails authenticate(String username, String password) {
        SecurityContext.clear();
        UserEntity user = username == null || username.isBlank() ? null : users.findByUsernameEq(username);
        boolean valid = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !valid) throw new LoginException("用户名或密码错误");
        if (user.getStatus() != UserStatus.ENABLED) throw new LoginException("账号已停用或锁定");
        AdminUserDetails details = load(user.getId());
        if (details == null) throw new LoginException("账号已停用或锁定");
        return details;
    }

    /** Token 恢复只按稳定用户 ID 查询，不使用用户名，不再次校验密码或建立登录。 */
    @Override
    public AdminUserDetails load(String userId) {
        if (userId == null || userId.isBlank()) return null;
        UserEntity user = users.findSnapshotById(userId);
        if (user == null || user.getStatus() != UserStatus.ENABLED) return null;
        if (!userId.equals(user.getId())) throw new IllegalStateException("查询 ID 与加载的用户 ID 不一致");
        return AdminUserDetails.from(user);
    }
}

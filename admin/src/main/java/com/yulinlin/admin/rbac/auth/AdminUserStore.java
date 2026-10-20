package com.yulinlin.admin.rbac.auth;

import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.security.auth.UserStore;
import org.springframework.stereotype.Component;

/** 每次请求读取最新状态和关联权限，不将密码或 ORM 代理交给安全上下文。 */
@Component
public class AdminUserStore implements UserStore {
    private final UserRepository users;

    public AdminUserStore(UserRepository users) {
        this.users = users;
    }

    @Override
    public AdminUserDetails load(String userId) {
        UserEntity user = users.findSnapshotById(userId);
        if (user == null || user.getStatus() != UserStatus.ENABLED) return null;
        return AdminUserDetails.from(user);
    }
}

package com.yulinlin.admin.rbac.auth;

import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.security.auth.IUserDetails;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 只保存不可变的用户信息快照，不保存 ORM 实体或事务代理。 */
@Getter
public final class AdminUserDetails implements IUserDetails {
    private final String userId;
    private final String username;
    private final String nickname;
    private final UserStatus status;
    private final Set<String> roles;
    private final Set<String> permissions;

    private AdminUserDetails(UserEntity user, Set<String> roles, Set<String> permissions) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.nickname = user.getNickname();
        this.status = user.getStatus();
        this.roles = Set.copyOf(roles);
        this.permissions = Set.copyOf(permissions);
    }

    public static AdminUserDetails from(UserEntity user) {
        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        for (RoleEntity role : user.getRoles() == null ? List.<RoleEntity>of() : user.getRoles()) {
            roles.add(role.getCode());
            if (role.getPermissions() != null) permissions.addAll(role.getPermissions());
        }
        return new AdminUserDetails(user, roles, permissions);
    }

    @Override
    public Object getAttribute(String name) {
        if (name == null) return null;
        return switch (name) {
            case "nickname" -> nickname;
            case "status" -> status;
            default -> null;
        };
    }
}

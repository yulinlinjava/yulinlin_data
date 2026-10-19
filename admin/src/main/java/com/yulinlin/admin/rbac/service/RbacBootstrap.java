package com.yulinlin.admin.rbac.service;

import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.admin.rbac.config.RbacProperties;
import com.yulinlin.admin.rbac.domain.Permission;
import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.RoleRepository;
import com.yulinlin.admin.rbac.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RbacBootstrap {
    private final RbacProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHash passwords;

    @Transactional
    public void initialize() {
        String password = properties.getBootstrapPassword();
        if (password == null || password.isBlank()) {
            log.info("未配置 ADMIN_RBAC_PASSWORD，跳过初始管理员创建");
            return;
        }
        String username = properties.getBootstrapUsername();
        if (username == null || !username.matches("[A-Za-z0-9_.-]{3,64}")) {
            throw new IllegalArgumentException("初始管理员用户名格式不正确");
        }
        if (roles.findByCodeEq(RoleService.ADMIN_ROLE) == null) {
            RoleEntity role = new RoleEntity();
            role.setCode(RoleService.ADMIN_ROLE);
            role.setName("系统管理员");
            role.setPermissions(Permission.codes());
            if (roles.insert(role) != 1) throw new IllegalStateException("初始管理员角色创建失败");
        }
        if (users.findByUsernameEq(username) != null) {
            log.info("初始管理员账号已存在，保留其密码、状态和角色");
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwords.encode(password));
        user.setNickname("系统管理员");
        user.setStatus(UserStatus.ENABLED);
        user.setRoleCodes(List.of(RoleService.ADMIN_ROLE));
        if (users.insert(user) != 1) throw new IllegalStateException("初始管理员账号创建失败");
        log.info("初始管理员创建成功，用户ID={}", user.getId());
    }
}

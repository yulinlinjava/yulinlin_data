package com.yulinlin.admin.rbac.service;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.domain.Permission;
import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.domain.UserRoleProjection;
import com.yulinlin.admin.rbac.repository.RoleRepository;
import com.yulinlin.admin.rbac.web.RbacRequests;
import com.yulinlin.admin.rbac.web.RoleResponse;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinSync;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.lang.util.DateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;

@Service("rbacRoleService")
@Profile("rbac")
@RequiredArgsConstructor
public class RoleService {
    public static final String ADMIN_ROLE = "admin";
    private final RoleRepository roles;
    private final AdminSecurityManager security;

    public List<RoleResponse> list() {
        return roles.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public String create(RbacRequests.CreateRole request) {
        if (ADMIN_ROLE.equals(request.code())) throw new IllegalArgumentException("admin 是内置管理员角色");
        if (roles.findByCodeEq(request.code()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "角色编码已存在");
        }
        RoleEntity role = new RoleEntity();
        role.setCode(request.code());
        role.setName(request.name());
        role.setPermissions(validPermissions(request.permissions()));
        if (roles.insert(role) != 1) throw new IllegalStateException("创建角色未成功写入");
        TransactionUtil.afterCommit(() -> security.refreshRole(request.code()));
        return role.getId();
    }

    @Transactional
    @JoinSync
    public void update(RbacRequests.UpdateRole request) {
        RoleEntity role = requireRole(request.id());
        rejectBuiltIn(role);
        role.setName(request.name());
        role.setPermissions(validPermissions(request.permissions()));
        role.setUptTime(DateTime.now());
        String code = role.getCode();
        TransactionUtil.afterCommit(() -> security.refreshRole(code));
    }

    @Transactional
    @JoinSync
    public void delete(String id) {
        RoleEntity role = requireRole(id);
        rejectBuiltIn(role);
        String code = role.getCode();
        // LIKE 先缩小 JSON 文本范围，再用集合 contains 精确确认（编码中的 _ 可能扩大 LIKE 匹配）。
        // 和删除角色处于同一个事务，避免将来重建同名角色时旧用户意外重新获得权限。
        List<UserRoleProjection> affected = ModelSelectWrapper.newInstance(UserRoleProjection.class)
                .like("roleCodes", "\"" + code + "\"").selectList();
        for (UserRoleProjection user : affected) {
            if (user.getRoleCodes() == null || !user.getRoleCodes().contains(code)) continue;
            user.setRoleCodes(user.getRoleCodes().stream().filter(value -> !value.equals(code)).toList());
            user.setUptTime(DateTime.now());
        }
        if (roles.deleteByIdEq(id) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "角色已变化，请刷新后重试");
        }
        TransactionUtil.afterCommit(() -> security.refreshRole(code));
    }

    private RoleEntity requireRole(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("角色ID不能为空");
        RoleEntity role = roles.findByIdEq(id);
        if (role == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
        return role;
    }

    private void rejectBuiltIn(RoleEntity role) {
        if (ADMIN_ROLE.equals(role.getCode())) throw new IllegalArgumentException("不能修改或删除内置管理员角色");
    }

    private List<String> validPermissions(List<String> permissions) {
        if (permissions == null || permissions.size() > 256
                || permissions.stream().anyMatch(permission -> !Permission.contains(permission))) {
            throw new IllegalArgumentException("存在未定义的权限编码");
        }
        return List.copyOf(new LinkedHashSet<>(permissions));
    }
}

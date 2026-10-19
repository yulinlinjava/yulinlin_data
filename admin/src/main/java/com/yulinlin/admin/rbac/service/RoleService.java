package com.yulinlin.admin.rbac.service;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.domain.Permission;
import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.domain.UserRoleProjection;
import com.yulinlin.admin.rbac.repository.RoleRepository;
import com.yulinlin.admin.rbac.web.RbacRequests;
import com.yulinlin.admin.rbac.web.RoleResponse;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.data.core.anno.JoinSync;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service("rbacRoleService")
@Transactional
@RequiredArgsConstructor
public class RoleService implements ISuperService<RoleEntity> {
    public static final String ADMIN_ROLE = "admin";
    private final RoleRepository roles;
    private final AdminSecurityManager security;

    public List<RoleResponse> list() {
        return roles.findAll().stream().map(RoleResponse::from).toList();
    }

    public RoleResponse detail(String id) { return RoleResponse.from(requireRole(id)); }

    public Page<RoleResponse> page(RbacRequests.RolePage query) {
        RbacValidation.validate(query, Set.of("code", "name", "crtTime", "uptTime"));
        var wrapper = ModelSelectWrapper.newInstance(RoleEntity.class, query);
        if (query.getOrderList() == null || query.getOrderList().isEmpty()) {
            wrapper.orderBy("code", true);
        } else {
            for (var order : query.getOrderList()) wrapper.orderBy(order.getName(), order.isAsc());
        }
        return wrapper.selectPage(query.getPageNumber(), query.getPageSize()).map(RoleResponse::from);
    }

    // 显式声明默认写方法，确保框架事务切面能拦截；最终统一进入批量校验入口。
    @Override public int insertOne(RoleEntity row) { return ISuperService.super.insertOne(row); }
    @Override public int updateOne(RoleEntity row) { return ISuperService.super.updateOne(row); }
    @Override public int save(RoleEntity row) { return ISuperService.super.save(row); }
    @Override public int saveList(List<RoleEntity> rows) { return ISuperService.super.saveList(rows); }

    @Override
    public int insertList(List<RoleEntity> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) return 0;
        Set<String> codes = new LinkedHashSet<>();
        for (RoleEntity role : rows) {
            Objects.requireNonNull(role, "role");
            if (role.getCode() == null || !role.getCode().matches("[a-z][a-z0-9:_-]{0,63}")) {
                throw new IllegalArgumentException("角色编码不正确");
            }
            rejectBuiltIn(role);
            requireName(role.getName());
            role.setPermissions(validPermissions(role.getPermissions()));
            if (!codes.add(role.getCode()) || roles.findByCodeEq(role.getCode()) != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "角色编码已存在或批次内重复");
            }
        }
        int count = roles.insertBatch(rows);
        requireCount(count, rows.size());
        for (String code : codes) TransactionUtil.afterCommit(() -> security.refreshRole(code));
        return count;
    }

    /** 只允许修改名称和权限，编码及审计创建时间不从调用方写回。 */
    @Override
    public int updateList(List<RoleEntity> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) return 0;
        List<RoleEntity> patches = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        for (RoleEntity row : rows) {
            Objects.requireNonNull(row, "role");
            RoleEntity current = requireRole(row.getId());
            rejectBuiltIn(current);
            if (!ids.add(current.getId())) throw new IllegalArgumentException("批次内角色ID不能重复");
            if (row.getCode() != null && !row.getCode().equals(current.getCode())) {
                throw new IllegalArgumentException("角色编码创建后不能修改");
            }
            if (row.getName() != null) requireName(row.getName());
            RoleEntity patch = new RoleEntity();
            patch.setId(current.getId());
            patch.setName(row.getName());
            if (row.getPermissions() != null) patch.setPermissions(validPermissions(row.getPermissions()));
            patch.setUptTime(DateTime.now());
            patches.add(patch);
            codes.add(current.getCode());
        }
        int count = roles.updateBatch(patches);
        requireCount(count, rows.size());
        for (String code : codes) TransactionUtil.afterCommit(() -> security.refreshRole(code));
        return count;
    }

    @Override
    @JoinSync
    public int deleteById(String id) { return deleteByIdIn(List.of(id)); }

    @Override
    @JoinSync
    public int deleteByIdIn(List<String> ids) {
        Objects.requireNonNull(ids, "ids");
        List<String> unique = List.copyOf(new LinkedHashSet<>(ids));
        if (unique.isEmpty()) return 0;
        Set<String> codes = new LinkedHashSet<>();
        for (String id : unique) {
            RoleEntity role = requireRole(id);
            rejectBuiltIn(role);
            codes.add(role.getCode());
        }
        // 同一个用户可能关联多个待删除角色，先按主键合并，再一次性移除全部编码。
        // 不能逐角色查询并修改代理，否则后一次查询的旧快照可能覆盖前一次的修改。
        var affected = new LinkedHashMap<String, UserRoleProjection>();
        for (String code : codes) {
            for (UserRoleProjection user : ModelSelectWrapper.newInstance(UserRoleProjection.class)
                    .like("roleCodes", "\"" + code + "\"").selectList()) {
                if (user.getRoleCodes() != null && user.getRoleCodes().contains(code)) {
                    affected.putIfAbsent(user.getId(), user);
                }
            }
        }
        for (UserRoleProjection user : affected.values()) {
            user.setRoleCodes(user.getRoleCodes().stream().filter(code -> !codes.contains(code)).toList());
            user.setUptTime(DateTime.now());
        }
        int count = roles.deleteByIdIn(unique);
        requireCount(count, unique.size());
        for (String code : codes) TransactionUtil.afterCommit(() -> security.refreshRole(code));
        return count;
    }

    @Transactional
    public String create(RbacRequests.CreateRole request) {
        RoleEntity role = new RoleEntity();
        role.setCode(request.code());
        role.setName(request.name());
        role.setPermissions(request.permissions());
        insertOne(role);
        return role.getId();
    }

    public List<String> createBatch(List<RbacRequests.CreateRole> requests) {
        RbacValidation.validateBatch(requests);
        List<RoleEntity> rows = requests.stream().map(request -> {
            RoleEntity role = new RoleEntity();
            role.setCode(request.code());
            role.setName(request.name());
            role.setPermissions(request.permissions());
            return role;
        }).toList();
        insertList(rows);
        return rows.stream().map(RoleEntity::getId).toList();
    }

    public int updateBatch(List<RbacRequests.UpdateRole> requests) {
        RbacValidation.validateBatch(requests);
        return updateList(requests.stream().map(request -> {
            requireName(request.name());
            RoleEntity role = new RoleEntity();
            role.setId(request.id());
            role.setName(request.name());
            role.setPermissions(validPermissions(request.permissions()));
            return role;
        }).toList());
    }

    @JoinSync
    public int deleteBatch(List<String> ids) {
        RbacValidation.validateBatch(ids);
        return deleteByIdIn(ids);
    }

    @Transactional
    @JoinSync
    public void update(RbacRequests.UpdateRole request) {
        RoleEntity role = requireRole(request.id());
        rejectBuiltIn(role);
        requireName(request.name());
        role.setName(request.name());
        role.setPermissions(validPermissions(request.permissions()));
        role.setUptTime(DateTime.now());
        String code = role.getCode();
        TransactionUtil.afterCommit(() -> security.refreshRole(code));
    }

    @Transactional
    @JoinSync
    public void delete(String id) {
        deleteById(id);
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

    private void requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 64) {
            throw new IllegalArgumentException("角色名称不能为空且最多64个字符");
        }
    }

    private void requireCount(int count, int expected) {
        if (count != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "角色数据已变化，请刷新后重试");
    }

    private List<String> validPermissions(List<String> permissions) {
        if (permissions == null || permissions.size() > 256
                || permissions.stream().anyMatch(permission -> !Permission.contains(permission))) {
            throw new IllegalArgumentException("存在未定义的权限编码");
        }
        return List.copyOf(new LinkedHashSet<>(permissions));
    }
}

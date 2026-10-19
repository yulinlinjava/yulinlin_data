package com.yulinlin.admin.rbac.service;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.auth.AdminUserDetails;
import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.RoleRepository;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.admin.rbac.web.RbacRequests;
import com.yulinlin.admin.rbac.web.UserResponse;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinSync;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service("rbacUserService")
@Profile("rbac")
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHash passwords;
    private final AdminSecurityManager security;

    public Page<UserResponse> page(RbacRequests.UserPage query) {
        if (query.getPageNumber() < 1 || query.getPageNumber() > 1000000
                || query.getPageSize() < 1 || query.getPageSize() > 100) {
            throw new IllegalArgumentException("页码必须为正数，每页最多查询 100 条");
        }
        var wrapper = ModelSelectWrapper.newInstance(UserEntity.class, query);
        if (query.getOrderList() == null || query.getOrderList().isEmpty()) {
            wrapper.orderBy("crtTime", false);
        } else {
            Set<String> sortable = Set.of("username", "nickname", "status", "crtTime", "uptTime");
            for (var order : query.getOrderList()) {
                if (order == null || !sortable.contains(order.getName())) {
                    throw new IllegalArgumentException("不支持此排序字段");
                }
                wrapper.orderBy(order.getName(), order.isAsc());
            }
        }
        return wrapper.selectPage(query.getPageNumber(), query.getPageSize()).map(UserResponse::from);
    }

    public UserResponse detail(String id) { return UserResponse.from(requireUser(id)); }

    @Transactional
    public String create(RbacRequests.CreateUser request) {
        if (users.findByUsernameEq(request.username()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setNickname(request.nickname());
        user.setStatus(UserStatus.ENABLED);
        user.setRoleCodes(validRoles(request.roleCodes()));
        if (users.insert(user) != 1) throw new IllegalStateException("创建用户未成功写入");
        log.info("创建用户成功，用户ID={}", user.getId());
        return user.getId();
    }

    @Transactional
    @JoinSync
    public void update(RbacRequests.UpdateUser request) {
        UserEntity user = requireUser(request.id());
        user.setNickname(request.nickname());
        user.setUptTime(DateTime.now());
        TransactionUtil.afterCommit(() -> security.refresh(request.id()));
    }

    @Transactional
    @JoinSync
    public void status(RbacRequests.ChangeStatus request) {
        if (request.status() != UserStatus.ENABLED) rejectSelf(request.id(), "不能停用或锁定当前登录账号");
        UserEntity user = requireUser(request.id());
        user.setStatus(request.status());
        user.setUptTime(DateTime.now());
        TransactionUtil.afterCommit(() -> security.revokeUser(request.id()));
    }

    @Transactional
    @JoinSync
    public void assignRoles(RbacRequests.AssignRoles request) {
        UserEntity user = requireUser(request.id());
        // 集合替换通过 setter 才能被自动更新代理跟踪，不原地修改 getRoleCodes()。
        user.setRoleCodes(validRoles(request.roleCodes()));
        user.setUptTime(DateTime.now());
        TransactionUtil.afterCommit(() -> security.refresh(request.id()));
    }

    @Transactional
    @JoinSync
    public void resetPassword(RbacRequests.ResetPassword request) {
        UserEntity user = requireUser(request.id());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setUptTime(DateTime.now());
        TransactionUtil.afterCommit(() -> security.revokeUser(request.id()));
    }

    @Transactional
    public void delete(String id) {
        rejectSelf(id, "不能删除当前登录账号");
        requireUser(id);
        if (users.deleteByIdEq(id) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户已变化，请刷新后重试");
        }
        TransactionUtil.afterCommit(() -> security.revokeUser(id));
    }

    private UserEntity requireUser(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("用户ID不能为空");
        UserEntity user = users.findByIdEq(id);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        return user;
    }

    private List<String> validRoles(Collection<String> codes) {
        if (codes == null || codes.size() > 64 || codes.stream().anyMatch(code -> code == null
                || !code.matches("[a-z][a-z0-9:_-]{0,63}"))) {
            throw new IllegalArgumentException("角色编码不正确");
        }
        List<String> unique = List.copyOf(new LinkedHashSet<>(codes));
        if (unique.isEmpty()) return unique;
        List<RoleEntity> found = roles.findByCodeIn(unique);
        if (found.size() != unique.size()) throw new IllegalArgumentException("存在未定义的角色");
        return unique;
    }

    private void rejectSelf(String id, String message) {
        AdminUserDetails current = security.getUserDetails();
        if (current != null && current.getUserId().equals(id)) throw new IllegalArgumentException(message);
    }
}

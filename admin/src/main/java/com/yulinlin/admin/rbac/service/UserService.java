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
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.data.core.anno.JoinSync;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service("rbacUserService")
@Transactional
@RequiredArgsConstructor
public class UserService implements ISuperService<UserEntity> {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHash passwords;
    private final AdminSecurityManager security;

    public Page<UserResponse> page(RbacRequests.UserPage query) {
        return select(query).selectPage(query.getPageNumber(), query.getPageSize()).map(UserResponse::from);
    }

    public List<UserResponse> list(RbacRequests.UserPage query) {
        return select(query).page(query.getPageNumber(), query.getPageSize())
                .selectList().stream().map(UserResponse::from).toList();
    }

    private ModelSelectWrapper<UserEntity, ?> select(RbacRequests.UserPage query) {
        RbacValidation.validate(query, Set.of("username", "nickname", "status", "crtTime", "uptTime"));
        ModelSelectWrapper<UserEntity, ?> wrapper = ModelSelectWrapper.newInstance(UserEntity.class, query);
        if (query.getOrderList() == null || query.getOrderList().isEmpty()) {
            wrapper.orderBy("crtTime", false);
        } else {
            for (var order : query.getOrderList()) {
                wrapper.orderBy(order.getName(), order.isAsc());
            }
        }
        return wrapper;
    }

    public UserResponse detail(String id) { return UserResponse.from(requireUser(id)); }

    // 明确声明通用写入口，使框架事务切面也能拦截接口 default 方法的调用。
    @Override public int insertOne(UserEntity row) { return ISuperService.super.insertOne(row); }
    @Override public int updateOne(UserEntity row) { return ISuperService.super.updateOne(row); }
    @Override public int save(UserEntity row) { return ISuperService.super.save(row); }
    @Override public int saveList(List<UserEntity> rows) { return ISuperService.super.saveList(rows); }
    @Override public int deleteById(String id) { return deleteByIdIn(List.of(id)); }

    @Override
    public int insertList(List<UserEntity> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) return 0;
        Set<String> usernames = new LinkedHashSet<>();
        for (UserEntity user : rows) {
            Objects.requireNonNull(user, "user");
            if (user.getUsername() == null || !user.getUsername().matches("[A-Za-z0-9_.-]{3,64}")) {
                throw new IllegalArgumentException("用户名格式不正确");
            }
            requireNickname(user.getNickname());
            requirePasswordHash(user.getPasswordHash());
            if (!usernames.add(user.getUsername()) || users.findByUsernameEq(user.getUsername()) != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在或批次内重复");
            }
            if (user.getStatus() == null) user.setStatus(UserStatus.ENABLED);
            user.setRoleCodes(validRoles(user.getRoleCodes() == null ? List.of() : user.getRoleCodes()));
        }
        int count = users.insertBatch(rows);
        requireCount(count, rows.size());
        return count;
    }

    /** 通用更新也保留用户名不可变、自身保护和提交后刷新规则，不回写创建时间。 */
    @Override
    public int updateList(List<UserEntity> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) return 0;
        List<UserEntity> patches = new ArrayList<>();
        Map<String, Boolean> invalidations = new LinkedHashMap<>();
        for (UserEntity row : rows) {
            Objects.requireNonNull(row, "user");
            UserEntity current = requireUser(row.getId());
            if (invalidations.containsKey(current.getId())) throw new IllegalArgumentException("批次内用户ID不能重复");
            if (row.getUsername() != null && !row.getUsername().equals(current.getUsername())) {
                throw new IllegalArgumentException("用户名创建后不能修改");
            }
            if (row.getNickname() != null) requireNickname(row.getNickname());
            if (row.getPasswordHash() != null) requirePasswordHash(row.getPasswordHash());
            if (row.getStatus() != null && row.getStatus() != UserStatus.ENABLED) {
                rejectSelf(row.getId(), "不能停用或锁定当前登录账号");
            }
            UserEntity patch = new UserEntity();
            patch.setId(current.getId());
            patch.setNickname(row.getNickname());
            patch.setPasswordHash(row.getPasswordHash());
            patch.setStatus(row.getStatus());
            if (row.getRoleCodes() != null) patch.setRoleCodes(validRoles(row.getRoleCodes()));
            patch.setUptTime(DateTime.now());
            patches.add(patch);
            invalidations.put(current.getId(), row.getStatus() != null || row.getPasswordHash() != null);
        }
        int count = users.updateBatch(patches);
        requireCount(count, rows.size());
        invalidations.forEach((id, revoke) -> TransactionUtil.afterCommit(() -> {
            if (revoke) security.revokeUser(id);
            else security.refresh(id);
        }));
        return count;
    }

    @Override
    public int deleteByIdIn(List<String> ids) {
        Objects.requireNonNull(ids, "ids");
        List<String> unique = List.copyOf(new LinkedHashSet<>(ids));
        if (unique.isEmpty()) return 0;
        for (String id : unique) {
            rejectSelf(id, "不能删除当前登录账号");
            requireUser(id);
        }
        int count = users.deleteByIdIn(unique);
        requireCount(count, unique.size());
        for (String id : unique) TransactionUtil.afterCommit(() -> security.revokeUser(id));
        return count;
    }

    @Transactional
    public String create(RbacRequests.CreateUser request) {
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setNickname(request.nickname());
        user.setStatus(UserStatus.ENABLED);
        user.setRoleCodes(request.roleCodes());
        insertOne(user);
        log.info("创建用户成功，用户ID={}", user.getId());
        return user.getId();
    }

    public List<String> createBatch(List<RbacRequests.CreateUser> requests) {
        RbacValidation.validateBatch(requests);
        List<UserEntity> rows = requests.stream().map(request -> {
            UserEntity user = new UserEntity();
            user.setUsername(request.username());
            user.setPasswordHash(passwords.encode(request.password()));
            user.setNickname(request.nickname());
            user.setStatus(UserStatus.ENABLED);
            user.setRoleCodes(request.roleCodes());
            return user;
        }).toList();
        insertList(rows);
        log.info("批量创建用户成功，数量={}", rows.size());
        return rows.stream().map(UserEntity::getId).toList();
    }

    public int updateBatch(List<RbacRequests.UpdateUser> requests) {
        RbacValidation.validateBatch(requests);
        return updateList(requests.stream().map(request -> {
            requireNickname(request.nickname());
            UserEntity user = new UserEntity();
            user.setId(request.id());
            user.setNickname(request.nickname());
            return user;
        }).toList());
    }

    public int deleteBatch(List<String> ids) {
        RbacValidation.validateBatch(ids);
        return deleteByIdIn(ids);
    }

    @Transactional
    @JoinSync
    public void update(RbacRequests.UpdateUser request) {
        requireNickname(request.nickname());
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
        deleteById(id);
    }

    private UserEntity requireUser(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("用户ID不能为空");
        UserEntity user = users.findByIdEq(id);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        return user;
    }

    private void requireNickname(String nickname) {
        if (nickname == null || nickname.length() > 64) throw new IllegalArgumentException("昵称最多64个字符");
    }

    private void requirePasswordHash(String encoded) {
        if (encoded == null || !encoded.startsWith("pbkdf2-sha256$") || encoded.length() > 255) {
            throw new IllegalArgumentException("Service 实体写入必须先用 PasswordHash.encode 生成密码哈希");
        }
    }

    private void requireCount(int count, int expected) {
        if (count != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "用户数据已变化，请刷新后重试");
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

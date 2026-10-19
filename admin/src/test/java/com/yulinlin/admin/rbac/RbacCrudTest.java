package com.yulinlin.admin.rbac;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.admin.rbac.domain.Permission;
import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserRoleProjection;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.RoleRepository;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.admin.rbac.service.RoleService;
import com.yulinlin.admin.rbac.service.UserService;
import com.yulinlin.admin.rbac.web.RbacRequests;
import com.yulinlin.admin.rbac.web.AuthController;
import com.yulinlin.admin.rbac.web.RoleController;
import com.yulinlin.admin.rbac.web.UserController;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.aop.SpringTransactionAop;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CRUD 业务校验、事务和 HTTP 契约；全部使用内存桩，不依赖数据库或 YAML。 */
class RbacCrudTest {
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AdminSecurityManager security = mock(AdminSecurityManager.class);
    private final PasswordHash passwords = mock(PasswordHash.class);
    private final RoleService roleService = new RoleService(roles, security);
    private final UserService userService = new UserService(users, roles, passwords, security);
    private final TransactionListenerManager manager = new TransactionListenerManager();

    @AfterEach void cleanTransaction() {
        while (TransactionListenerManager.currentContext() != null) {
            TransactionListenerManager.currentContext().manager().rollbackTransaction();
        }
    }

    @Test void servicesExposeTheFrameworkCrudContract() {
        assertThat(roleService).isInstanceOf(ISuperService.class);
        assertThat(userService).isInstanceOf(ISuperService.class);
        assertThat(roleService.getEntityClass()).isEqualTo(RoleEntity.class);
        assertThat(userService.getEntityClass()).isEqualTo(UserEntity.class);
    }

    @Test void genericRoleUpdatesAndDeletesCannotBypassBuiltInRoleProtection() {
        RoleEntity admin = role("admin-id", "admin");
        when(roles.findByIdEq("admin-id")).thenReturn(admin);
        RoleEntity patch = role("admin-id", "admin");
        assertThatThrownBy(() -> roleService.updateOne(patch)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roleService.deleteById("admin-id")).isInstanceOf(IllegalArgumentException.class);
        verify(roles, never()).updateBatch(any());
        verify(roles, never()).deleteByIdEq(any());
        verify(roles, never()).deleteByIdIn(any());
        verifyNoInteractions(security);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deletingMultipleRolesMergesChangesForTheSameUser() {
        manager.startTransaction();
        when(roles.findByIdEq("role-1")).thenReturn(role("role-1", "reader"));
        when(roles.findByIdEq("role-2")).thenReturn(role("role-2", "auditor"));
        when(roles.deleteByIdIn(List.of("role-1", "role-2"))).thenReturn(2);
        UserRoleProjection first = new UserRoleProjection();
        first.setId("user-1");
        first.setRoleCodes(List.of("reader", "auditor", "other"));
        UserRoleProjection second = new UserRoleProjection();
        second.setId("user-1");
        second.setRoleCodes(List.of("reader", "auditor", "other"));
        ModelSelectWrapper query = mock(ModelSelectWrapper.class, RETURNS_SELF);
        when(query.selectList()).thenReturn(List.of(first), List.of(second));
        try (var model = mockStatic(ModelSelectWrapper.class)) {
            model.when(() -> ModelSelectWrapper.newInstance(UserRoleProjection.class)).thenReturn(query);
            assertThat(roleService.deleteByIdIn(List.of("role-1", "role-2"))).isEqualTo(2);
            assertThat(first.getRoleCodes()).containsExactly("other");
        }
        verifyNoInteractions(security);
        manager.commitTransaction();
        verify(security).refreshRole("reader");
        verify(security).refreshRole("auditor");
    }

    @Test void genericRoleUpdateWritesOnlyAllowedFieldsAndRefreshesAfterCommit() {
        manager.startTransaction();
        when(roles.findByIdEq("role-1")).thenReturn(role("role-1", "reader"));
        when(roles.updateBatch(any())).thenAnswer(invocation -> {
            List<RoleEntity> rows = invocation.getArgument(0);
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().getCode()).isNull();
            assertThat(rows.getFirst().getCrtTime()).isNull();
            assertThat(rows.getFirst().getName()).isEqualTo("新名称");
            return 1;
        });
        RoleEntity patch = role("role-1", "reader");
        patch.setName("新名称");
        patch.setCrtTime(DateTime.now());
        assertThat(roleService.updateOne(patch)).isEqualTo(1);
        verifyNoInteractions(security);
        manager.commitTransaction();
        verify(security).refreshRole("reader");
    }

    @Test void userGenericBatchUpdateRefreshesOrRevokesEachAffectedSessionAfterCommit() {
        manager.startTransaction();
        when(users.findByIdEq("user-1")).thenReturn(user("user-1", "alice"));
        when(users.findByIdEq("user-2")).thenReturn(user("user-2", "bob"));
        when(users.updateBatch(any())).thenReturn(2);
        UserEntity first = new UserEntity();
        first.setId("user-1");
        first.setNickname("新昵称");
        UserEntity second = new UserEntity();
        second.setId("user-2");
        second.setStatus(UserStatus.DISABLED);
        assertThat(userService.updateList(List.of(first, second))).isEqualTo(2);
        verify(security, never()).refresh(anyString());
        verify(security, never()).revokeUser(anyString());
        manager.commitTransaction();
        verify(security).refresh("user-1");
        verify(security).revokeUser("user-2");
    }

    @Test void rollbackDoesNotRefreshPermissions() {
        manager.startTransaction();
        when(roles.findByIdEq("role-1")).thenReturn(role("role-1", "reader"));
        when(roles.updateBatch(any())).thenReturn(1);
        roleService.updateOne(role("role-1", "reader"));
        manager.rollbackTransaction();
        verifyNoInteractions(security);
    }

    @Test void defaultCrudWriteIsInterceptedByTheFrameworkTransactionAspect() {
        RouteSession previous = SessionUtil.route();
        new SessionUtil(RouteSession.builder().build());
        try {
            var proxyFactory = new AspectJProxyFactory(roleService);
            proxyFactory.setProxyTargetClass(true);
            proxyFactory.addAspect(new SpringTransactionAop());
            RoleService proxy = proxyFactory.getProxy();
            when(roles.insertBatch(any())).thenAnswer(invocation -> {
                assertThat(TransactionListenerManager.currentContext()).isNotNull();
                return 1;
            });
            doAnswer(invocation -> {
                assertThat(TransactionListenerManager.currentContext()).isNull();
                return null;
            }).when(security).refreshRole("reader");
            assertThat(proxy.insertOne(role("role-1", "reader"))).isEqualTo(1);
            assertThat(TransactionListenerManager.currentContext()).isNull();
            verify(security).refreshRole("reader");
        } finally {
            new SessionUtil(previous);
        }
    }

    @Test void httpBatchSizeIsLimitedBeforeAnyDatabaseWork() {
        var request = new RbacRequests.CreateRole("reader", "只读", List.of("user:query"));
        assertThatThrownBy(() -> roleService.createBatch(Collections.nCopies(101, request)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userService.createBatch(List.of())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roles, users, passwords);
    }

    @Test void nestedBatchDtosAreValidated() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var batch = new RbacRequests.Batch<>(List.of(
                    new RbacRequests.CreateRole("bad code", "角色", List.of("user:query"))));
            assertThat(factory.getValidator().validate(batch)).isNotEmpty();
        }
    }

    @Test void allCrudHttpEndpointsDeclareTheirOwnPermissionsAndUsePost() {
        for (Class<?> controller : List.of(RoleController.class, UserController.class)) {
            assertThat(controller.getAnnotation(ApiLogin.class)).isNotNull();
            boolean role = controller == RoleController.class;
            for (String method : List.of("page", "list", "detail")) {
                assertEndpoint(controller, method, role ? Permission.ROLE_QUERY : Permission.USER_QUERY);
            }
            for (String method : List.of("create", "createBatch")) {
                assertEndpoint(controller, method, role ? Permission.ROLE_CREATE : Permission.USER_CREATE);
            }
            for (String method : List.of("update", "updateBatch")) {
                assertEndpoint(controller, method, role ? Permission.ROLE_UPDATE : Permission.USER_UPDATE);
            }
            for (String method : List.of("delete", "deleteBatch")) {
                assertEndpoint(controller, method, role ? Permission.ROLE_DELETE : Permission.USER_DELETE);
            }
        }
    }

    @Test void authControllerRequiresLoginExceptForTheLoginMethod() {
        assertThat(AuthController.class.getAnnotation(ApiLogin.class)).isNotNull();
        var login = Arrays.stream(AuthController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("login")).findFirst().orElseThrow();
        assertThat(login.getAnnotation(ApiPublic.class)).isNotNull();
        for (var method : AuthController.class.getDeclaredMethods()) {
            if (!method.getName().equals("login")) assertThat(method.getAnnotation(ApiPublic.class)).isNull();
        }
    }

    private void assertEndpoint(Class<?> controller, String name, Permission permission) {
        var method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/" + name);
        assertThat(method.getAnnotation(ApiPerm.class).value()).containsExactly(permission.getCode());
    }

    private RoleEntity role(String id, String code) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setCode(code);
        role.setName("角色");
        role.setPermissions(List.of("user:query"));
        return role;
    }

    private UserEntity user(String id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}

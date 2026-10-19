package com.yulinlin.admin.rbac;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.admin.rbac.config.RbacProperties;
import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.security.auth.LoginException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminSecurityManagerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHash passwords = mock(PasswordHash.class);
    private AdminSecurityManager security;
    private UserEntity user;
    private RoleEntity role;

    @BeforeEach
    void prepare() {
        when(passwords.encode(anyString())).thenReturn("dummy");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        security = new AdminSecurityManager(users, passwords, new RbacProperties());
        role = new RoleEntity();
        role.setCode("reader");
        role.setPermissions(List.of("user:query"));
        user = new UserEntity();
        user.setId("user-1");
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ENABLED);
        user.setRoles(List.of(role));
        when(users.findByUsernameEq("alice")).thenReturn(user);
        when(users.findByIdEq("user-1")).thenReturn(user);
        when(users.findSnapshotById("user-1")).thenReturn(user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void refreshesRolePermissionsWithoutIssuingANewTokenAndLogoutRevokesIt() {
        var login = security.signIn("alice", "correct", null);
        authenticate(login.accessToken());
        assertThat(security.getUserDetails().hasPermission("user:query")).isTrue();

        role.setPermissions(List.of());
        security.refreshRole("reader");
        assertThat(security.getUserDetails().getPermissions()).isEmpty();
        assertThat(login.user().hasPermission("user:query")).isTrue(); // 原快照保持不可变。

        security.logout();
        authenticate(login.accessToken());
        assertThat(security.getUserDetails()).isNull();
    }

    @Test
    void rejectsInvalidCredentialsAndDisabledUsers() {
        assertThatThrownBy(() -> security.signIn("alice", "wrong", null)).isInstanceOf(LoginException.class);
        assertThatThrownBy(() -> security.signIn("missing", "wrong", null)).isInstanceOf(LoginException.class);
        user.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> security.signIn("alice", "correct", null)).isInstanceOf(LoginException.class);
    }

    @Test
    void refreshRevokesDisabledUsersAndDatabaseFailuresDoNotKeepOldPrivileges() {
        var login = security.signIn("alice", "correct", null);
        authenticate(login.accessToken());
        user.setStatus(UserStatus.LOCKED);
        security.refresh("user-1");
        assertThat(security.getUserDetails()).isNull();

        user.setStatus(UserStatus.ENABLED);
        login = security.signIn("alice", "correct", null);
        authenticate(login.accessToken());
        when(users.findSnapshotById("user-1")).thenThrow(new IllegalStateException("数据库不可用"));
        assertThatThrownBy(() -> security.refresh("user-1")).isInstanceOf(IllegalStateException.class);
        assertThat(security.getUserDetails()).isNull();
    }

    private void authenticate(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}

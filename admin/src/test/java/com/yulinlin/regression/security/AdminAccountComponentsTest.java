package com.yulinlin.regression.security;

import com.yulinlin.admin.rbac.auth.AdminAuthenticationService;
import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.UserAuthenticator;
import com.yulinlin.security.auth.UserStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 单一业务 Bean 的两个协议入口；只使用内存桩，不读取 YAML 或连接数据库。 */
class AdminAccountComponentsTest {
    @AfterEach void cleanup() { SecurityContext.clear(); }

    @Test void bothInterfacesResolveToTheSameBusinessBean() {
        UserRepository users = mock(UserRepository.class);
        PasswordHash passwords = passwords();
        new ApplicationContextRunner()
                .withBean(UserRepository.class, () -> users)
                .withBean(PasswordHash.class, () -> passwords)
                .withBean(AdminAuthenticationService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(UserStore.class)
                            .hasSingleBean(UserAuthenticator.class);
                    assertSame(context.getBean(UserAuthenticator.class), context.getBean(UserStore.class));
                });
    }

    @Test void authenticationUsesUsernameButRestorationUsesIdEvenAfterRenaming() {
        UserRepository users = mock(UserRepository.class);
        PasswordHash passwords = passwords();
        UserEntity account = user("db-user-42", "alice");
        when(users.findByUsernameEq("alice")).thenReturn(account);
        when(users.findSnapshotById("db-user-42")).thenReturn(account);
        AdminAuthenticationService service = new AdminAuthenticationService(users, passwords);

        assertEquals("db-user-42", service.authenticate("alice", "correct").getUserId());
        verify(users).findByUsernameEq("alice");
        verify(passwords).matches("correct", "hash");

        clearInvocations(users, passwords);
        account.setUsername("alice-new");
        var restored = service.load("db-user-42");
        assertEquals("db-user-42", restored.getUserId());
        assertEquals("alice-new", restored.getUsername());
        verify(users).findSnapshotById("db-user-42");
        verify(users, never()).findByUsernameEq(anyString());
        verifyNoInteractions(passwords);
    }

    @Test void missingDisabledAndMismatchedUsersCannotBecomeLoginSnapshots() {
        UserRepository users = mock(UserRepository.class);
        PasswordHash passwords = passwords();
        AdminAuthenticationService service = new AdminAuthenticationService(users, passwords);
        assertNull(service.load(null));
        assertNull(service.load(" "));
        verifyNoInteractions(users);
        assertNull(service.load("missing"));

        UserEntity account = user("u1", "alice");
        account.setStatus(UserStatus.DISABLED);
        when(users.findSnapshotById("u1")).thenReturn(account);
        when(users.findByUsernameEq("alice")).thenReturn(account);
        assertNull(service.load("u1"));
        assertThrows(LoginException.class, () -> service.authenticate("alice", "correct"));

        account.setStatus(UserStatus.ENABLED);
        when(users.findSnapshotById("u1")).thenReturn(user("another-user", "bob"));
        assertThrows(IllegalStateException.class, () -> service.load("u1"));
        assertThrows(IllegalStateException.class, () -> service.authenticate("alice", "correct"));
    }

    private static PasswordHash passwords() {
        PasswordHash passwords = mock(PasswordHash.class);
        when(passwords.encode(anyString())).thenReturn("dummy");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        return passwords;
    }

    private static UserEntity user(String id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ENABLED);
        return user;
    }
}

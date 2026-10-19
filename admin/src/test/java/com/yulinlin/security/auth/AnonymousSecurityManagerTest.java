package com.yulinlin.security.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AnonymousSecurityManagerTest {
    private final ISecurityManager manager = new AnonymousSecurityManager();

    @Test void allReadsRemainAnonymousAndLogoutDoesNothing() {
        assertThat(manager.getUserDetails()).isNull();
        assertThat(manager.getUserDetails("user-1")).isNull();
        assertThat(manager.refresh()).isNull();
        assertThat(manager.refresh("user-1")).isNull();
        assertThatCode(manager::logout).doesNotThrowAnyException();
    }

    @Test void neitherLoginOverloadPretendsToAuthenticate() {
        assertThatThrownBy(() -> manager.login("admin", "password"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("未配置业务 ISecurityManager");
        assertThatThrownBy(() -> manager.login("admin", "password", "code"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("未配置业务 ISecurityManager");
    }
}

package com.yulinlin.admin.rbac;

import com.yulinlin.admin.rbac.auth.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashTest {
    @Test
    void usesIndependentSaltAndRejectsWrongPasswordsOrMalformedHashes() {
        PasswordHash passwords = new PasswordHash();
        String first = passwords.encode("Example-Password-2026");
        String second = passwords.encode("Example-Password-2026");

        assertThat(first).isNotEqualTo(second).startsWith("pbkdf2-sha256$600000$");
        assertThat(passwords.matches("Example-Password-2026", first)).isTrue();
        assertThat(passwords.matches("Wrong-Password-2026", first)).isFalse();
        assertThat(passwords.matches(null, first)).isFalse();
        assertThat(passwords.matches("Example-Password-2026", "invalid")).isFalse();
    }
}

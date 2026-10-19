package com.yulinlin.data.core.coder.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanCoderTest {
    private final BooleanCoder coder = new BooleanCoder();

    @Test
    void decodesDatabaseBooleanRepresentations() {
        assertThat(decode(false)).isFalse();
        assertThat(decode(true)).isTrue();
        assertThat(decode("FALSE")).isFalse();
        assertThat(decode("TRUE")).isTrue();
        assertThat(decode("0")).isFalse();
        assertThat(decode("1")).isTrue();
        assertThat(decode(0)).isFalse();
        assertThat(decode(-1)).isTrue();
        assertThat(decode(new byte[]{0})).isFalse();
        assertThat(decode(new byte[]{1})).isTrue();
    }

    @Test
    void rejectsUnknownBooleanTextInsteadOfSilentlyChangingItsMeaning() {
        assertThatThrownBy(() -> decode("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported boolean value");
    }

    private Boolean decode(Object value) {
        return coder.decode(null, null, value);
    }
}

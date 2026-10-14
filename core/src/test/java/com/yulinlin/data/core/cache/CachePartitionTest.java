package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.session.AbstractSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CachePartitionTest {

    private static final CacheNamespace USERS = CacheNamespace.of(AbstractSession.class, "mysql", "users");

    @Test
    void textBucketsConservativelyIgnoreCaseAndSurroundingWhitespace() {
        CachePartition first = partition("User-A  ");
        CachePartition sameBucket = partition("user-a");
        CachePartition leadingWhitespace = partition(" user-a");

        assertThat(first).isEqualTo(sameBucket).isEqualTo(leadingWhitespace);
    }

    @Test
    void equivalentNumericTypesShareOneInvalidationBucket() {
        assertThat(partition(1))
                .isEqualTo(partition(1L))
                .isEqualTo(partition(new BigDecimal("1.000")))
                .isEqualTo(partition("01.0"));
    }

    @Test
    void fieldUsesTheNormalizedSimpleColumnName() {
        assertThat(CachePartition.of(USERS, "User_Name", "a")).contains(
                CachePartition.of(USERS, "user_name", "A ").orElseThrow());
    }

    @Test
    void complexFieldNamesAreRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> CachePartition.of(USERS, "payload.name", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void complexValuesAreNotPartitioned() {
        assertThat(CachePartition.of(USERS, "metadata", Map.of("role", "admin"))).isEmpty();
    }

    private static CachePartition partition(Object value) {
        return CachePartition.of(USERS, "username", value).orElseThrow();
    }
}

package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.repository.anno.JoinCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JoinCacheOptionsTest {

    private final MethodParseManager manager = new MethodParseManager();

    @Test
    void absenceOfAnnotationAlwaysDisablesCaching() throws Exception {
        JoinCacheOptions options = JoinCacheOptions.from(ValidRepository.class
                .getMethod("findByNameEq", String.class));

        assertThat(options.enabled()).isFalse();
        assertThat(options.mode()).isEqualTo(CacheMode.NONE);
        assertThat(options.ttl()).isNull();
    }

    @Test
    void parsesRefreshTtlAndAdditionalNamespaces() throws Exception {
        Method method = ValidRepository.class.getMethod("findByIdEq", String.class);
        JoinCacheOptions options = JoinCacheOptions.from(method);

        assertThat(options.enabled()).isTrue();
        assertThat(options.mode()).isEqualTo(CacheMode.REFRESH);
        assertThat(options.ttl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(options.namespaces()).containsExactly("roles", "permissions");
        manager.validate(ValidRepository.class);
    }

    @Test
    void defaultAnnotationUsesReadThroughAndSystemTtl() throws Exception {
        JoinCacheOptions options = JoinCacheOptions.from(ValidRepository.class
                .getMethod("findAll"));

        assertThat(options.mode()).isEqualTo(CacheMode.READ_THROUGH);
        assertThat(options.ttl()).isNull();
    }

    @Test
    void rejectsCacheAnnotationOnWriteMethodDuringRepositoryCreation() {
        assertThatThrownBy(() -> manager.validate(WriteRepository.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("write method")
                .hasMessageContaining("deleteByIdEq");
    }

    @Test
    void rejectsInvalidTtlDuringRepositoryCreation() {
        assertThatThrownBy(() -> manager.validate(InvalidTtlRepository.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl must be -1 or positive")
                .hasMessageContaining("findByIdEq");
    }

    @Test
    void rejectsBlankNamespaceDuringRepositoryCreation() {
        assertThatThrownBy(() -> manager.validate(BlankNamespaceRepository.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace must not be blank");
    }

    interface ValidRepository {
        @JoinCache(mode = CacheMode.REFRESH, ttl = 2, unit = TimeUnit.MINUTES,
                namespaces = {" roles ", "permissions", "roles"})
        User findByIdEq(String id);

        @JoinCache
        List<User> findAll();

        User findByNameEq(String name);
    }

    interface WriteRepository {
        @JoinCache
        int deleteByIdEq(String id);
    }

    interface InvalidTtlRepository {
        @JoinCache(ttl = 0)
        User findByIdEq(String id);
    }

    interface BlankNamespaceRepository {
        @JoinCache(namespaces = " ")
        User findByIdEq(String id);
    }

    record User(String id) {
    }
}

package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.anno.JoinCache;
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
        JoinCacheOptions options = JoinCacheOptions.from(ValidRepository.class,
                ValidRepository.class.getMethod("findByNameEq", String.class));

        assertThat(options.enabled()).isFalse();
        assertThat(options.mode()).isEqualTo(CacheMode.NONE);
        assertThat(options.ttl()).isNull();
    }

    @Test
    void parsesRefreshTtlAndAdditionalNamespaces() throws Exception {
        Method method = ValidRepository.class.getMethod("findByIdEq", String.class);
        JoinCacheOptions options = JoinCacheOptions.from(ValidRepository.class, method);

        assertThat(options.enabled()).isTrue();
        assertThat(options.mode()).isEqualTo(CacheMode.REFRESH);
        assertThat(options.ttl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(options.namespaces()).containsExactly("roles", "permissions");
        manager.validate(ValidRepository.class);
    }

    @Test
    void defaultAnnotationUsesReadThroughAndSystemTtl() throws Exception {
        JoinCacheOptions options = JoinCacheOptions.from(ValidRepository.class,
                ValidRepository.class.getMethod("findAll"));

        assertThat(options.mode()).isEqualTo(CacheMode.READ_THROUGH);
        assertThat(options.ttl()).isNull();
    }

    @Test
    void repositoryAnnotationProvidesDefaultsOnlyForQueries() throws Exception {
        Method query = CachedRepository.class.getMethod("findByNameEq", String.class);
        JoinCacheOptions options = JoinCacheOptions.from(CachedRepository.class, query);

        assertThat(options.enabled()).isTrue();
        assertThat(options.mode()).isEqualTo(CacheMode.READ_THROUGH);
        assertThat(options.ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(options.namespaces()).containsExactly("users");
        manager.validate(CachedRepository.class); // Its unannotated write method remains valid.
    }

    @Test
    void methodAnnotationCanDisableRepositoryDefault() throws Exception {
        JoinCacheOptions options = JoinCacheOptions.from(CachedRepository.class,
                CachedRepository.class.getMethod("findByIdEq", String.class));

        assertThat(options.enabled()).isTrue();
        assertThat(options.mode()).isEqualTo(CacheMode.NONE);
        assertThat(options.ttl()).isNull();
        assertThat(options.namespaces()).isEmpty();
    }

    @Test
    void repositoryAndMethodDefaultsAreInheritedFromParentInterfaces() throws Exception {
        JoinCacheOptions typeOptions = JoinCacheOptions.from(InheritedCachedRepository.class,
                InheritedCachedRepository.class.getMethod("findByNameEq", String.class));
        JoinCacheOptions methodOptions = JoinCacheOptions.from(InheritedMethodRepository.class,
                InheritedMethodRepository.class.getMethod("findByIdEq", String.class));

        assertThat(typeOptions.ttl()).isEqualTo(Duration.ofMinutes(3));
        assertThat(methodOptions.mode()).isEqualTo(CacheMode.REFRESH);
        assertThat(methodOptions.ttl()).isEqualTo(Duration.ofSeconds(20));
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

    @Test
    void rejectsInvalidRepositoryDefaultDuringRepositoryCreation() {
        assertThatThrownBy(() -> manager.validate(InvalidDefaultRepository.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl must be -1 or positive")
                .hasMessageContaining(InvalidDefaultRepository.class.getName());
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

    @JoinCache(ttl = 5, unit = TimeUnit.MINUTES, namespaces = "users")
    interface CachedRepository {
        User findByNameEq(String name);

        @JoinCache(mode = CacheMode.NONE)
        User findByIdEq(String id);

        int deleteByIdEq(String id);
    }

    @JoinCache(ttl = 3, unit = TimeUnit.MINUTES)
    interface CachedParent {
        User findByNameEq(String name);
    }

    interface InheritedCachedRepository extends CachedParent {
    }

    interface CachedMethodParent {
        @JoinCache(mode = CacheMode.REFRESH, ttl = 20)
        User findByIdEq(String id);
    }

    interface InheritedMethodRepository extends CachedMethodParent {
        @Override
        User findByIdEq(String id);
    }

    interface InvalidTtlRepository {
        @JoinCache(ttl = 0)
        User findByIdEq(String id);
    }

    interface BlankNamespaceRepository {
        @JoinCache(namespaces = " ")
        User findByIdEq(String id);
    }

    @JoinCache(ttl = 0)
    interface InvalidDefaultRepository {
        User findAll();
    }

    record User(String id) {
    }
}

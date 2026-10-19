package com.yulinlin.data.core.request;

import com.yulinlin.data.core.model.BaseModelDeleteWrapper;
import com.yulinlin.data.core.model.BaseModelInsertWrapper;
import com.yulinlin.data.core.model.BaseModelUpdateWrapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WriteCacheApiTest {

    @Test
    void executeRequestUsesOnlyTheClearCacheWriteApi() {
        ExecuteRequest<?> request = ExecuteRequest.ofUpdate(Object.class);

        assertThat(request.getClearCache()).isNull();
        assertThat(request.clearCache(false)).isSameAs(request);
        assertThat(request.getClearCache()).isFalse();
        assertThat(request.shouldClearCache(true)).isFalse();
        request.setClearCache(null);
        assertThat(request.shouldClearCache(true)).isTrue();
        assertThat(request.shouldClearCache(false)).isFalse();

        assertNoLegacyWriteCacheMethods(ExecuteRequest.class);
        assertThat(Arrays.stream(ExecuteRequest.class.getMethods()).map(Method::getName))
                .doesNotContain("isInvalidate", "setInvalidate", "isInvalidateAll", "setInvalidateAll");
    }

    @Test
    void replacementRequestKeepsItsChoiceUnlessTheOriginalExplicitlyOverridesIt() {
        ExecuteRequest<?> replacement = ExecuteRequest.ofUpdate(Object.class).clearCache(false);
        replacement.inheritExecutionOptions(ExecuteRequest.ofUpdate(Object.class));
        assertThat(replacement.getClearCache()).isFalse();

        replacement.clearCache(true)
                .inheritExecutionOptions(ExecuteRequest.ofUpdate(Object.class).clearCache(false));
        assertThat(replacement.getClearCache()).isFalse();

        replacement.clearCache(false)
                .inheritExecutionOptions(ExecuteRequest.ofUpdate(Object.class).clearCache(true));
        assertThat(replacement.getClearCache()).isTrue();
    }

    @Test
    void writeWrappersExposeClearCacheAndNotTheLegacyAliases() {
        for (Class<?> type : Set.of(
                BaseModelInsertWrapper.class,
                BaseModelUpdateWrapper.class,
                BaseModelDeleteWrapper.class)) {
            assertThat(Arrays.stream(type.getMethods()))
                    .anyMatch(method -> method.getName().equals("clearCache")
                            && Arrays.equals(method.getParameterTypes(), new Class<?>[]{boolean.class}));
            assertNoLegacyWriteCacheMethods(type);
        }
    }

    private static void assertNoLegacyWriteCacheMethods(Class<?> type) {
        Set<String> methods = Arrays.stream(type.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(methods).doesNotContain("cache", "invalidate", "invalidateAll");
    }
}

package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinTableList;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CacheNamespaceResolverTest {

    @Test
    void resolvesAllAnnotatedAndExplicitResources() {
        Set<CacheNamespace> result = CacheNamespaceResolver.resolve(
                CacheNamespaceResolverTest.class,
                "reporting",
                JoinedView.class,
                List.of(),
                List.of("audit_log a"));

        assertThat(result).extracting(CacheNamespace::resource)
                .containsExactlyInAnyOrder("orders", "users", "roles", "audit_log");
        assertThat(result).allSatisfy(namespace -> {
            assertThat(namespace.group()).isEqualTo("reporting");
            assertThat(namespace.sessionType()).isEqualTo(CacheNamespaceResolverTest.class.getName());
        });
    }

    @Test
    void ignoresMissingSourceClass() {
        assertThat(CacheNamespaceResolver.resolve(
                CacheNamespaceResolverTest.class, "master", Object.class,
                List.of(), List.of())).isEmpty();
    }

    @Test void physicalNodeTablesOverrideTheAnnotationFallback() {
        var wrapper = new com.yulinlin.data.core.wrapper.impl.SelectWrapper<>().table("orders_202610");
        assertThat(CacheNamespaceResolver.resolve(CacheNamespaceResolverTest.class,
                "mysql", JoinedView.class, wrapper, List.of("extra_dependency")))
                .extracting(CacheNamespace::resource)
                .containsExactlyInAnyOrder("orders_202610", "extra_dependency");
    }

    @JoinTableList({
            @JoinTable(value = "orders o"),
            @JoinTable(left = "users u", right = "roles r", on = "u.role_id = r.id")
    })
    private static final class JoinedView {
    }
}

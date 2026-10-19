package com.yulinlin.security.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 只检查注解声明契约，不包含 AOP、HTTP 或鉴权行为测试。 */
class ApiAuthAnnotationsTest {

    @Test void annotationsAreDocumentedAndRetainedOnTypesAndMethods() {
        for (Class<?> annotation : List.of(ApiLogin.class, ApiRole.class, ApiPerm.class, ApiPublic.class)) {
            assertThat(annotation.isAnnotation()).isTrue();
            assertThat(annotation.isAnnotationPresent(Documented.class)).isTrue();
            assertThat(annotation.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
            assertThat(annotation.getAnnotation(Target.class).value())
                    .containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);
        }
    }

    @Test void loginAndPublicAreMarkerAnnotations() throws NoSuchMethodException {
        assertThat(ApiLogin.class.getDeclaredMethods()).isEmpty();
        assertThat(ApiPublic.class.getDeclaredMethods()).isEmpty();
        assertThat(ProtectedApi.class.getAnnotation(ApiLogin.class)).isNotNull();
        assertThat(PublicApi.class.getAnnotation(ApiPublic.class)).isNotNull();
        assertThat(LoginApi.class.getDeclaredMethod("login").getAnnotation(ApiPublic.class)).isNotNull();
        assertThat(ProtectedApi.class.getDeclaredMethod("anyAccess").getAnnotation(ApiLogin.class)).isNotNull();
    }

    @Test void rolesAndPermissionsDefaultToRequiringAllValues() throws NoSuchMethodException {
        ApiRole role = ProtectedApi.class.getAnnotation(ApiRole.class);
        ApiPerm perm = ProtectedApi.class.getAnnotation(ApiPerm.class);
        assertThat(role.value()).containsExactly("admin");
        assertThat(role.any()).isFalse();
        assertThat(perm.value()).containsExactly("user:query", "user:update");
        assertThat(perm.any()).isFalse();
        assertThat(ApiRole.class.getDeclaredMethod("value").getDefaultValue()).isNull();
        assertThat(ApiPerm.class.getDeclaredMethod("value").getDefaultValue()).isNull();
    }

    @Test void methodsCanDeclareAnyRoleOrPermission() throws NoSuchMethodException {
        var method = ProtectedApi.class.getDeclaredMethod("anyAccess");
        ApiRole role = method.getAnnotation(ApiRole.class);
        ApiPerm perm = method.getAnnotation(ApiPerm.class);
        assertThat(role.value()).containsExactly("admin", "operator");
        assertThat(role.any()).isTrue();
        assertThat(perm.value()).containsExactly("video:update", "video:audit");
        assertThat(perm.any()).isTrue();
    }

    @ApiLogin
    @ApiRole("admin")
    @ApiPerm({"user:query", "user:update"})
    static class ProtectedApi {
        @ApiLogin
        @ApiRole(value = {"admin", "operator"}, any = true)
        @ApiPerm(value = {"video:update", "video:audit"}, any = true)
        void anyAccess() { }
    }

    @ApiLogin
    static class LoginApi {
        @ApiPublic
        void login() { }
    }

    @ApiPublic
    static class PublicApi { }
}

package com.yulinlin.security.aop;

import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.annotation.ApiRole;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实 Spring 代理验证鉴权，不连接数据库或执行 Spring YAML 属性绑定。 */
class ApiAuthAspectTest {
    private final ISecurityManager manager = mock(ISecurityManager.class);

    @Test void unannotatedAndPublicMethodsNeverResolveCurrentUser() {
        LoginService service = proxy(new LoginService(), manager);
        assertThat(service.login()).isEqualTo("public");
        assertThat(proxy(new OpenService(), manager).read()).isEqualTo("plain");
        verifyNoInteractions(manager);
    }

    @Test void anonymousManagerDeniesLoginRolesAndPermissions() {
        ISecurityManager anonymous = new AnonymousSecurityManager();
        denied(() -> proxy(new LoginService(), anonymous).read(), 401);
        Checks checks = proxy(new Checks(), anonymous);
        denied(checks::allRoles, 401);
        denied(checks::allPermissions, 401);
    }

    @Test void rolesAndPermissionsSupportAllAndAnyWithoutImplicitAdministratorBypass() {
        authenticate(Set.of("operator"), Set.of("user:query"));
        Checks checks = proxy(new Checks(), manager);
        assertThat(checks.anyRole()).isEqualTo("ok");
        assertThat(checks.anyPermission()).isEqualTo("ok");
        denied(checks::allRoles, 403);
        denied(checks::allPermissions, 403);

        authenticate(Set.of("admin", "operator"), Set.of("user:query", "user:update"));
        assertThat(checks.allRoles()).isEqualTo("ok");
        assertThat(checks.allPermissions()).isEqualTo("ok");
        authenticate(Set.of("admin"), Set.of());
        denied(checks::bothRoleAndPermission, 403);
    }

    @Test void aProtectedInvocationLoadsTheCurrentUserOnceAndDoesNotCacheIdentity() {
        authenticate(Set.of("admin"), Set.of("user:update"));
        Checks checks = proxy(new Checks(), manager);
        assertThat(checks.bothRoleAndPermission()).isEqualTo("ok");
        verify(manager, times(1)).getUserDetails();
        doReturn(null).when(manager).getUserDetails();
        denied(checks::bothRoleAndPermission, 401);
        verify(manager, times(2)).getUserDetails();
    }

    @Test void classAndMethodRequirementsAreBothRequired() {
        authenticate(Set.of("admin"), Set.of("user:update"));
        ClassRules service = proxy(new ClassRules(), manager);
        denied(service::update, 403);
        authenticate(Set.of("admin", "operator"), Set.of("user:query", "user:update"));
        assertThat(service.update()).isEqualTo("ok");
    }

    @Test void publicClassDoesNotRemoveExplicitMethodProtection() {
        PublicService service = proxy(new PublicService(), manager);
        assertThat(service.open()).isEqualTo("public");
        verifyNoInteractions(manager);
        denied(service::restricted, 401);
    }

    @Test void classLoginAlsoProtectsInheritedDefaultMethods() {
        DefaultService service = proxy(new DefaultService(), manager);
        denied(service::read, 401);
        authenticate(Set.of(), Set.of());
        assertThat(service.read()).isEqualTo("default");
    }

    @Test void interfaceTypeAnnotationsWorkWithJdkProxies() {
        ProxyFactory factory = new ProxyFactory(new InterfaceServiceImpl());
        factory.setInterfaces(InterfaceService.class);
        factory.addAdvisor(new ApiAuthAspect(() -> manager));
        InterfaceService service = (InterfaceService) factory.getProxy();
        denied(service::read, 401);
        authenticate(Set.of(), Set.of());
        assertThat(service.read()).isEqualTo("interface");
    }

    @Test
    @SuppressWarnings("unchecked")
    void genericInterfaceAnnotationsResolveThroughBridgeMethods() {
        ProxyFactory factory = new ProxyFactory(new GenericService());
        factory.setInterfaces(GenericApi.class);
        factory.addAdvisor(new ApiAuthAspect(() -> manager));
        GenericApi<String> service = (GenericApi<String>) factory.getProxy();
        denied(() -> service.read("value"), 401);
        authenticate(Set.of(), Set.of("user:query"));
        assertThat(service.read("value")).isEqualTo("value");
    }

    @Test void superclassMethodRequirementsSurviveAnUnannotatedOverride() {
        InheritedService service = proxy(new InheritedService(), manager);
        denied(service::read, 401);
        authenticate(Set.of(), Set.of("user:query"));
        assertThat(service.read()).isEqualTo("child");
    }

    @Test void metadataValidationRejectsEmptyValuesConflictsAndFinalMethods() throws Exception {
        ApiAuthAspect aspect = new ApiAuthAspect(() -> manager);
        for (String name : new String[]{"empty", "blank", "conflict", "finalMethod"}) {
            assertThatThrownBy(() -> aspect.matches(InvalidService.class.getMethod(name), InvalidService.class))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("鉴权注解配置错误");
        }
        assertThatThrownBy(() -> aspect.matches(PublicOverRole.class.getMethod("open"), PublicOverRole.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不能覆盖");
        assertThatThrownBy(() -> aspect.matches(FinalService.class.getMethod("read"), FinalService.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("final");
        verifyNoInteractions(manager);
    }

    @Test void errorsFromCurrentUserLookupAreNotReportedAsInvalidCredentials() {
        IllegalStateException failure = new IllegalStateException("认证存储暂不可用");
        when(manager.getUserDetails()).thenThrow(failure);
        LoginService service = proxy(new LoginService(), manager);
        assertThatThrownBy(service::read).isSameAs(failure);
    }

    @Test void currentUserLookupCannotRecursivelyCallProtectedServices() {
        AtomicReference<LoginService> reference = new AtomicReference<>();
        when(manager.getUserDetails()).thenAnswer(invocation -> {
            reference.get().read();
            return null;
        });
        reference.set(proxy(new LoginService(), manager));
        assertThatThrownBy(() -> reference.get().read())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不能再调用受鉴权保护的方法");
        doReturn(null).when(manager).getUserDetails();
        denied(() -> reference.get().read(), 401); // 异常后线程状态已经清理。
    }

    @Test void authenticationRunsBeforeTransactionAndSyncAdvisors() {
        assertThat(ApiAuthAspect.ORDER).isLessThan(9998);
        assertThat(new ApiAuthAspect(() -> manager).getOrder()).isEqualTo(ApiAuthAspect.ORDER);
    }

    private void authenticate(Set<String> roles, Set<String> permissions) {
        IUserDetails user = mock(IUserDetails.class, CALLS_REAL_METHODS);
        when(user.getRoles()).thenReturn(roles);
        when(user.getPermissions()).thenReturn(permissions);
        when(manager.getUserDetails()).thenReturn(user);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, ISecurityManager security) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new ApiAuthAspect(() -> security));
        return (T) factory.getProxy();
    }

    private void denied(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(status));
    }

    @ApiLogin
    public static class LoginService {
        @ApiPublic public String login() { return "public"; }
        public String read() { return "protected"; }
    }

    public static class OpenService {
        public String read() { return "plain"; }
    }

    public static class Checks {
        @ApiRole({"admin", "operator"}) public String allRoles() { return "ok"; }
        @ApiRole(value = {"admin", "operator"}, any = true) public String anyRole() { return "ok"; }
        @ApiPerm({"user:query", "user:update"}) public String allPermissions() { return "ok"; }
        @ApiPerm(value = {"user:query", "user:update"}, any = true) public String anyPermission() { return "ok"; }
        @ApiRole("admin") @ApiPerm("user:update") public String bothRoleAndPermission() { return "ok"; }
    }

    @ApiRole("operator")
    @ApiPerm("user:query")
    public static class ClassRules {
        @ApiRole("admin") @ApiPerm("user:update") public String update() { return "ok"; }
    }

    @ApiPublic
    public static class PublicService {
        public String open() { return "public"; }
        @ApiPerm("user:query") public String restricted() { return "protected"; }
    }

    public interface DefaultApi {
        default String read() { return "default"; }
    }

    @ApiLogin
    public static class DefaultService implements DefaultApi { }

    @ApiLogin
    public interface InterfaceService { String read(); }
    public static class InterfaceServiceImpl implements InterfaceService {
        @Override public String read() { return "interface"; }
    }

    public interface GenericApi<T> {
        @ApiPerm("user:query") T read(T value);
    }
    public static class GenericService implements GenericApi<String> {
        @Override public String read(String value) { return value; }
    }

    public static class ParentService {
        @ApiPerm("user:query") public String read() { return "parent"; }
    }
    public static class InheritedService extends ParentService {
        @Override public String read() { return "child"; }
    }

    public static class InvalidService {
        @ApiPerm({}) public void empty() { }
        @ApiRole(" ") public void blank() { }
        @ApiPublic @ApiLogin public void conflict() { }
        @ApiLogin public final void finalMethod() { }
    }
    @ApiRole("admin")
    public static class PublicOverRole {
        @ApiPublic public void open() { }
    }
    @ApiLogin
    public static final class FinalService {
        public void read() { }
    }
}

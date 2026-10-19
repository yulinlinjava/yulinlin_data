package com.yulinlin.admin.rbac;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.auth.AdminUserDetails;
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.aop.ApiAuthAspect;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RbacAuthAopTest {
    @Test
    void distinguishesAnonymousUnauthenticatedAndMissingPermission() throws Exception {
        AdminSecurityManager security = mock(AdminSecurityManager.class);
        ProxyFactory factory = new ProxyFactory(new Endpoints());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new ApiAuthAspect(() -> security));
        Endpoints endpoints = (Endpoints) factory.getProxy();

        endpoints.login();
        verifyNoInteractions(security);
        assertThatThrownBy(endpoints::query)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));

        AdminUserDetails user = mock(AdminUserDetails.class);
        when(security.getUserDetails()).thenReturn(user);
        assertThatThrownBy(endpoints::query)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));

        when(user.hasPermission("user:query")).thenReturn(true);
        endpoints.query();
    }

    @ApiLogin
    public static class Endpoints {
        @ApiPublic
        public void login() { }

        @ApiPerm("user:query")
        public void query() { }
    }
}

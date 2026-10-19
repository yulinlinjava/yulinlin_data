package com.yulinlin.admin.rbac;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.auth.AdminUserDetails;
import com.yulinlin.admin.rbac.auth.Anonymous;
import com.yulinlin.admin.rbac.auth.RbacInterceptor;
import com.yulinlin.admin.rbac.auth.RequirePermission;
import com.yulinlin.admin.rbac.domain.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RbacInterceptorTest {
    @Test
    void distinguishesAnonymousUnauthenticatedAndMissingPermission() throws Exception {
        AdminSecurityManager security = mock(AdminSecurityManager.class);
        RbacInterceptor interceptor = new RbacInterceptor(security);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rbac/example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Endpoints endpoints = new Endpoints();
        HandlerMethod login = new HandlerMethod(endpoints, Endpoints.class.getMethod("login"));
        HandlerMethod query = new HandlerMethod(endpoints, Endpoints.class.getMethod("query"));

        assertThat(interceptor.preHandle(request, response, login)).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request, response, query))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));

        AdminUserDetails user = mock(AdminUserDetails.class);
        when(security.getUserDetails()).thenReturn(user);
        assertThatThrownBy(() -> interceptor.preHandle(request, response, query))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));

        when(user.hasPermission("user:query")).thenReturn(true);
        assertThat(interceptor.preHandle(request, response, query)).isTrue();
    }

    public static class Endpoints {
        @Anonymous
        public void login() { }

        @RequirePermission(Permission.USER_QUERY)
        public void query() { }
    }
}

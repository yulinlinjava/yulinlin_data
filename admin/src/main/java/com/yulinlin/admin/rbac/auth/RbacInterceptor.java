package com.yulinlin.admin.rbac.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** /rbac/** 下默认需要登录；管理接口还必须声明具体权限。 */
public final class RbacInterceptor implements HandlerInterceptor {
    private final AdminSecurityManager security;

    public RbacInterceptor(AdminSecurityManager security) {
        this.security = security;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), Anonymous.class)) return true;
        AdminUserDetails user = security.getUserDetails();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录或重新登录");
        RequirePermission required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequirePermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequirePermission.class);
        }
        if (required != null && !user.hasPermission(required.value().getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有操作权限");
        }
        return true;
    }
}

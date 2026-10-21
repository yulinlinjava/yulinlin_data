package com.yulinlin.security.spring;

import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.web.LoginInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 只维护框架快照的请求边界；凭证解析、认证及 Spring 上下文清理由原有过滤器链负责。 */
public final class SpringLoginInterceptor implements LoginInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SecurityContext.clear();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception error) {
        SecurityContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SecurityContext.clear();
    }
}

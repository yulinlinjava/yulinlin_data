package com.yulinlin.security.web;

import com.yulinlin.security.auth.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

public final class LoginContextWebMvcConfigurer implements WebMvcConfigurer {
    private final LoginInterceptor interceptor;

    public LoginContextWebMvcConfigurer(LoginInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor, "登录拦截器不能为空");
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        // 只注册被选中的一个实现；即使业务忘记清理，身份也不能泄漏到下一个请求。
        registry.addInterceptor(new ContextLifecycleInterceptor(interceptor))
                .order(Ordered.HIGHEST_PRECEDENCE + 50);
    }

    private record ContextLifecycleInterceptor(LoginInterceptor delegate) implements AsyncHandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            SecurityContext.clear();
            boolean accepted = false;
            try {
                accepted = delegate.preHandle(request, response, handler);
                return accepted;
            } finally {
                // 返回 false 或抛异常时，MVC 不会调用本拦截器的 afterCompletion。
                if (!accepted) SecurityContext.clear();
            }
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response,
                               Object handler, ModelAndView modelAndView) throws Exception {
            delegate.postHandle(request, response, handler, modelAndView);
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception exception) throws Exception {
            try { delegate.afterCompletion(request, response, handler, exception); }
            finally { SecurityContext.clear(); }
        }

        @Override
        public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                                   Object handler) throws Exception {
            try { delegate.afterConcurrentHandlingStarted(request, response, handler); }
            finally { SecurityContext.clear(); }
        }
    }
}

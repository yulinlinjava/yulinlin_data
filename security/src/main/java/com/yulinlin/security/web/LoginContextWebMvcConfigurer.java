package com.yulinlin.security.web;

import com.yulinlin.security.aop.ApiAuthPolicy;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

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

    private static boolean isStaticResource(Object handler) {
        // 按 MVC 实际处理器判定，不能通过文件后缀或 /static/** 放行 Controller。
        return handler instanceof ResourceHttpRequestHandler
                || handler instanceof DefaultServletHttpRequestHandler;
    }

    private record ContextLifecycleInterceptor(LoginInterceptor delegate) implements AsyncHandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            SecurityContext.clear();
            // 静态资源不恢复登录身份，也不调用自定义登录拦截器。
            if (isStaticResource(handler)) return true;
            boolean accepted = false;
            try {
                // 公开入口也先恢复身份，有效 Token 不能因 ApiPublic 被直接跳过。
                accepted = delegate.preHandle(request, response, handler);
                return accepted;
            } catch (LoginException error) {
                SecurityContext.clear();
                // 只兼容明确的认证失败；不吞数据库/缓存异常，不接管已输出的响应。
                if (!response.isCommitted() && response.getStatus() < 400
                        && handler instanceof HandlerMethod method
                        && ApiAuthPolicy.allowsAnonymous(method.getMethod(), method.getBeanType())) {
                    accepted = true;
                    return true;
                }
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录", error);
            } finally {
                // 返回 false 或抛异常时，MVC 不会调用本拦截器的 afterCompletion。
                if (!accepted) SecurityContext.clear();
            }
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response,
                               Object handler, ModelAndView modelAndView) throws Exception {
            if (!isStaticResource(handler)) delegate.postHandle(request, response, handler, modelAndView);
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception exception) throws Exception {
            try {
                if (!isStaticResource(handler)) delegate.afterCompletion(request, response, handler, exception);
            }
            finally { SecurityContext.clear(); }
        }

        @Override
        public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                                   Object handler) throws Exception {
            try {
                if (!isStaticResource(handler)) delegate.afterConcurrentHandlingStarted(request, response, handler);
            }
            finally { SecurityContext.clear(); }
        }
    }
}

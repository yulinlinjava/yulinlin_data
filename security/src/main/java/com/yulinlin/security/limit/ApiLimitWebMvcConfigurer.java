package com.yulinlin.security.limit;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers API limiting before ordinary application interceptors. */
public final class ApiLimitWebMvcConfigurer implements WebMvcConfigurer {

    private final ApiLimitInterceptor interceptor;

    public ApiLimitWebMvcConfigurer(ApiLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE + 100);
    }
}

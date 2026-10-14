package com.yulinlin.monitor;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies the monitor's small, session-based login check. */
final class MonitorWebMvcConfigurer implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MonitorLoginInterceptor())
                .addPathPatterns("/monitor/api/**")
                .excludePathPatterns("/monitor/api/login");
    }
}

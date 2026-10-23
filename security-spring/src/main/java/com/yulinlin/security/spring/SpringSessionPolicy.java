package com.yulinlin.security.spring;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionLimit;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * 原生 DSL 的数量、注册表没有公开 getter，仅在这个兼容边界只读检查配置，不反射修改 Spring 状态。
 * 不执行用户提供的 SessionLimit 函数来猜测是否相等；统一上限只能有一个配置来源。
 * Spring 内部字段发生变化时明确失败，不能跳过校验继续启动。
 */
final class SpringSessionPolicy {
    private final SessionManagementConfigurer<?> configurer;
    private final SessionRegistry registry;
    private final SecurityContextRepository repository;
    private SessionLimit limit;

    SpringSessionPolicy(HttpSecurity http, SessionManagementConfigurer<?> configurer,
                        SessionRegistry registry, SecurityContextRepository repository) {
        this.configurer = configurer;
        this.registry = registry;
        this.repository = repository;
        if (read("sessionLimit") != null) {
            throw conflict("maximumSessions/SessionLimit 已配置；上限只能通过 yulinlin.security.session.max-sessions 设置");
        }
        Object nativeRegistry = read("sessionRegistry");
        if (nativeRegistry != null && nativeRegistry != registry) throw conflict("SessionRegistry 与管理器使用的 Bean 不一致");
        checkStateful(http);
        SecurityContextRepository nativeRepository = http.getSharedObject(SecurityContextRepository.class);
        if (nativeRepository != null && nativeRepository != repository) {
            throw conflict("SecurityContextRepository 与统一登录使用的 Bean 不一致");
        }
    }

    void captureLimit() {
        if (!(read("sessionLimit") instanceof SessionLimit configuredLimit)) {
            throw conflict("无法获取统一设置的 SessionLimit");
        }
        limit = configuredLimit;
    }

    boolean needsDefaultExpiredStrategy() {
        return read("expiredSessionStrategy") == null && read("expiredUrl") == null;
    }

    void validate(HttpSecurity http) {
        if (http.getConfigurer(SessionManagementConfigurer.class) != configurer) {
            throw conflict("不能移除或替换已接入的 SessionManagementConfigurer");
        }
        if (http.getConfigurer(SecurityContextConfigurer.class) == null) {
            throw conflict("不能关闭 SecurityContext 配置");
        }
        checkStateful(http);
        if (read("sessionLimit") != limit) throw conflict("接入后重新设置了 maximumSessions/SessionLimit");
        if (read("sessionRegistry") != registry) throw conflict("接入后重新设置了 SessionRegistry");
        if (http.getSharedObject(SecurityContextRepository.class) != repository) {
            throw conflict("接入后重新设置了 SecurityContextRepository");
        }
    }

    private void checkStateful(HttpSecurity http) {
        Object policy = read("sessionPolicy");
        if (policy == null) policy = http.getSharedObject(SessionCreationPolicy.class);
        if (policy == SessionCreationPolicy.STATELESS || policy == SessionCreationPolicy.NEVER) {
            throw conflict("统一会话登录需要 IF_REQUIRED 或 ALWAYS，不能使用 " + policy);
        }
    }

    private Object read(String name) {
        Field field = ReflectionUtils.findField(SessionManagementConfigurer.class, name);
        if (field == null) throw conflict("当前 Spring Security 版本无法校验原生字段 " + name + "，请使用已验证的版本或自定义适配");
        try {
            ReflectionUtils.makeAccessible(field);
            return ReflectionUtils.getField(field, configurer);
        } catch (RuntimeException error) {
            throw new IllegalStateException("无法校验 Spring Security 会话配置字段 " + name, error);
        }
    }

    private static IllegalStateException conflict(String detail) {
        return new IllegalStateException("Spring 原生配置与统一会话策略冲突：" + detail
                + "；也可以设置 yulinlin.security.session.enabled=false 后自行管理原生会话");
    }
}

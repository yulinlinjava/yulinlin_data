package com.yulinlin.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明调用方必须登录，可用于 Controller 或 Service 的类和方法。
 * 由 ApiAuthAspect 检查，经 Spring Bean 代理调用时生效。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiLogin {
}

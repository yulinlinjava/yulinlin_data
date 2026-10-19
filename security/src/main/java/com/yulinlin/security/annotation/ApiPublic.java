package com.yulinlin.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明允许匿名访问，不表示关闭加密、限流或跳过角色与权限要求。
 * 方法标记可豁免类级 ApiLogin，与有效角色或权限要求冲突时拒绝配置。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiPublic {
}

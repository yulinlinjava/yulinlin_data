package com.yulinlin.admin.rbac.auth;

import com.yulinlin.admin.rbac.domain.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 使用枚举防止接口权限编码拼写错误；执行精确匹配。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequirePermission {
    Permission value();
}

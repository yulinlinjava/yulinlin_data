package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 懒加载注解
 * 延迟查询使用对象
 *
 */
@Retention(RUNTIME)
@Target(value={FIELD})
public @interface JoinLazy {



}

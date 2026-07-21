package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 对象懒同步注解
 * 如果对象头加注解，该实例一定被代理
 * 如果字段加注解，字段值被代理
 */
@Retention(RUNTIME)
@Target(value={FIELD,TYPE})
public @interface JoinSync {



}

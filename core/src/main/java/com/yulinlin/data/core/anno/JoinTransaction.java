package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 自定义事务
 */
@Retention(RUNTIME)
@Target(value={TYPE,METHOD})
public @interface JoinTransaction {


    String[] value() default {};

}

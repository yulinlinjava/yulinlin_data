package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 */
@Retention(RUNTIME)
@Target(value={FIELD})
public @interface JoinOrder {

	String name() default "";

	boolean asc() default false;

}

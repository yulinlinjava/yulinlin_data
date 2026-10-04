package com.yulinlin.data.core.anno;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 快速增删改查用
 */
@Retention(RUNTIME)
@Target(value={TYPE})
@Inherited
public @interface JoinTableList {

	JoinTable[] value() ;

}

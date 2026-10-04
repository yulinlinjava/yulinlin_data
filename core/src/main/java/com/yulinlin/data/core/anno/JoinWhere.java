package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 条件对象
 */
@Retention(RUNTIME)
@Target(value={FIELD})
public @interface JoinWhere {

	/**
	 * 条件符号，默认eq
	 * @return
	 */
	ConditionEnum condition() default  ConditionEnum.eq;


	//使用and链接
	boolean and() default  true;

	/**
	 * 级联查询使用
	 * @return
	 */
	//字段名
	String name() default "";

	//取值字段
	 String value() default "" ;



}

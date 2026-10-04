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
public @interface JoinTable {

	String value() default "";

	/**
	 * 单，多可以使用
	 * 当符合使用时，第2个后面不用写
	 * @return
	 */
	String left() default "" ;

	/**
	 * 多可以使用
	 * @return
	 */
	String right() default "";

	/**
	 * 链接条件
	 * @return
	 */
	String on() default "";

	//链接模式
	JoinEnum join() default JoinEnum.left;


}

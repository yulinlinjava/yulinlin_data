package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *  关联查询使用
 */
@Retention(RUNTIME)
@Target(value={FIELD})
public @interface JoinQuery {

	Class<?> model() default Object.class;

	
	int size() default -1;

	JoinOrder[] order() default {};

	//主键字段名
	String primary() default "id";

	//外键字段名
	String value() default "" ;

	//条件列表，复杂查询使用   和上面二选一
	JoinWhere[] wheres() default {};
}

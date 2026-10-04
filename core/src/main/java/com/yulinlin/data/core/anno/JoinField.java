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
public @interface JoinField {



	//列明
	String name() default "";
	//函数
	String function() default "";

	//数据库是否够存在该字段
	boolean exist() default  true;

	//是否允许字段更新
	boolean update() default  true;

	//是否乐观锁
	boolean version() default false;

	//更新操作类型
	UpdateTypeEnum updateType() default UpdateTypeEnum.set;


}

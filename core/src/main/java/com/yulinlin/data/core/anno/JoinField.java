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

	/** 文本存储类型；仅影响启动 Schema 创建与校验，不改变编解码器。 */
	TextTypeEnum textType() default TextTypeEnum.auto;

	/** VARCHAR 最大字符数；0 表示沿用数据库默认值。 */
	int textLength() default 0;

	/** 数据库列用途说明；MySQL/H2 写入列注释，SQLite 无原生列注释。 */
	String description() default "";

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

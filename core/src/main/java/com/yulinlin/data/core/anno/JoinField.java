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

	/**
	 * 新建 VARCHAR 的最大字符数；已有列容量大于等于显式长度即可兼容。
	 * 容量不足默认报错，显式开启数据源 alter-column-type 后可在 CREATE 模式扩容。
	 * 0 表示使用框架/方言默认值（字符串主键默认 32），不校验已有列长度；SQLite 不强制长度。
	 */
	int textLength() default 0;

	/** 数据库列用途说明；MySQL/H2 写入列注释，SQLite 无原生列注释。 */
	String description() default "";

	/**
	 * 声明该文本列需要全文索引。PostgreSQL 与 Elasticsearch 模块实现；其他数据源会忽略此属性。
	 */
	boolean fullText() default false;

	//函数
	String function() default "";

	//数据库是否够存在该字段
	boolean exist() default  true;

	/** 是否允许字段进入 UPDATE SET；主键字段仍会用于 WHERE 条件。 */
	boolean update() default  true;

	//是否乐观锁
	boolean version() default false;

	//更新操作类型
	UpdateTypeEnum updateType() default UpdateTypeEnum.set;


}

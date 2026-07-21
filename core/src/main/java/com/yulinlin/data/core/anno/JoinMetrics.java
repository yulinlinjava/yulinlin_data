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
public @interface JoinMetrics {



	//调用函数格式化
	MetricsEnum value();



}

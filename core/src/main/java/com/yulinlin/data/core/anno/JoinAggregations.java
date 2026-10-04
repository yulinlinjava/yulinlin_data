package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 聚合设计
 */
@Retention(RUNTIME)
@Target(value={FIELD})
public @interface JoinAggregations {




    AggregationsEnum value() default AggregationsEnum.field;

    //区间
    int interval() default 5;
}

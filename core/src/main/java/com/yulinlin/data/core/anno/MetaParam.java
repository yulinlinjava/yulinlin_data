package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 字段标注
 *
 */
@Retention(RUNTIME)
@Target(value={FIELD})
public @interface MetaParam {


    String name();

    String value();

}

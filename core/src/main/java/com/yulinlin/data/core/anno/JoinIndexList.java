package com.yulinlin.data.core.anno;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Container used by Java for repeatable {@link JoinIndex} declarations. */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface JoinIndexList {
    JoinIndex[] value();
}

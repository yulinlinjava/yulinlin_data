package com.yulinlin.data.core.anno;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Declares one ordinary or unique index for an automatically managed entity table. */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(JoinIndexList.class)
public @interface JoinIndex {
    /** Java property names; their order is the composite-index column order. */
    String[] fields();

    boolean unique() default false;
}

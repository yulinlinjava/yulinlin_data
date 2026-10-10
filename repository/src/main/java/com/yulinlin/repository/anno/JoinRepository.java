package com.yulinlin.repository.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface JoinRepository {
    /** Optional Spring bean name; useful when repositories share a simple class name. */
    String value() default "";
}

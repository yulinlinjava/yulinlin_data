package com.yulinlin.data.core.anno;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an interface for discovery by the optional Repository module. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JoinRepository {

    /** Optional Spring bean name; useful when repositories share a simple class name. */
    String value() default "";
}

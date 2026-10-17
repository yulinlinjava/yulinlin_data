package com.yulinlin.security.annotation;

import com.yulinlin.security.limit.LimitBy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Limits calls to one controller method by client IP or device identifier. */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiLimit {

    /** Allowed requests in one interval. */
    int count() default 60;

    /** Spring duration text such as 500ms, 10s, 1m or 1h. */
    String interval() default "1m";

    /** Client dimension used to isolate counters. */
    LimitBy by() default LimitBy.IP;
}

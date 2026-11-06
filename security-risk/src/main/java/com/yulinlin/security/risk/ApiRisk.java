package com.yulinlin.security.risk;

import java.lang.annotation.*;

/** Checks before controller/business execution; a rejected decision throws {@link ApiRiskException}. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ApiRisk {
    String value();
    /** Optional resource field: path variable, request parameter, then JSON field (e.g. videoId or video.id). */
    String resource() default "";
}


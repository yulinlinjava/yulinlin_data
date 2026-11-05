package com.yulinlin.security.risk;

import java.lang.annotation.*;

/** Protect a controller endpoint after authentication, before its method or business transaction executes. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ApiRisk {
    String value();
    /** Optional query parameter or MVC path-variable name. Mutually exclusive with resourceBody. */
    String resourceParam() default "";
    /** Optional JSON field path, e.g. videoId or video.id, read after body conversion and decryption. */
    String resourceBody() default "";
}


package com.yulinlin.data.core.anno;

import com.yulinlin.data.core.cache.CacheMode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/** Configures query caching for a Repository interface or one Repository query method. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface JoinCache {

    CacheMode mode() default CacheMode.READ_THROUGH;

    /** -1 uses yulinlin.cache.ttl; otherwise the value must be positive. */
    long ttl() default -1;

    TimeUnit unit() default TimeUnit.SECONDS;

    /** Additional resources whose namespace versions participate in the cache key. */
    String[] namespaces() default {};
}

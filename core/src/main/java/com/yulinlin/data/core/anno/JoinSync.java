package com.yulinlin.data.core.anno;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Enables automatic entity updates for every query executed in the annotated method or type.
 * A transaction must still be opened with {@code @Transactional}, {@code @JoinTransaction},
 * or the programmatic RouteSession transaction API.
 */
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Inherited
public @interface JoinSync {
}

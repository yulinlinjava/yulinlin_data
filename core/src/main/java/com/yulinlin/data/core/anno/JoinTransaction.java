package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * ORM 事务，等同于 {@code RouteSession.transaction(...)}。事务按实际访问自动登记数据源，可以跨多个数据源组。
 */
@Retention(RUNTIME)
@Target(value={TYPE,METHOD})
public @interface JoinTransaction {


    /** 不再使用：数据源按访问自动登记，写了会在启动时报错。 */
    String[] value() default {};

    /**
     * 事务超时（秒），-1 表示不设置。事务内每条 JDBC 语句按剩余时间设置查询超时，时间用完后语句失败、事务回滚。
     * 与 {@code @Transactional(timeout)} 同时存在时以 {@code @Transactional} 为准。
     */
    int timeout() default -1;

}

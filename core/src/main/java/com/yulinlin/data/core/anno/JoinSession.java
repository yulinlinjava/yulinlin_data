package com.yulinlin.data.core.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 动态数据源注解
 * 当再模型类使用，代表该模型查询使用数据源
 * 如果模型嵌套，则模型数据源独立，事务互相隔离
 *
 */
@Retention(RUNTIME)
@Target(value={TYPE,METHOD,FIELD})
public @interface JoinSession {

    /**
     * 数据源名称
     * @return
     */
    String value() ;

    /**
     * 集群类型
     * @return
     */
    JoinCluster cluster() default JoinCluster.master;

}

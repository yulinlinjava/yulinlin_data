package com.yulinlin.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明调用方必须登录并拥有指定权限，权限编码按大小写精确匹配。
 * 由 ApiAuthAspect 检查；类、接口和方法上的要求叠加，不互相覆盖。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiPerm {

    /** 所需权限编码，单个值可写为 {@code @ApiPerm("user:query")}。 */
    String[] value();

    /** 默认全部满足；为 true 时，满足任意一个权限即可。 */
    boolean any() default false;
}

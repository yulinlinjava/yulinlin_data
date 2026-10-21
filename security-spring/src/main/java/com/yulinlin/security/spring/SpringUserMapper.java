package com.yulinlin.security.spring;

import com.yulinlin.security.auth.IUserDetails;
import org.springframework.security.core.Authentication;

/** 将已经认证的 Spring 身份转换为安全用户 DTO；业务可提供 Bean 替换默认映射。 */
@FunctionalInterface
public interface SpringUserMapper {
    /**
     * 只会传入已认证且非匿名的 Authentication。返回 null 表示不允许作为框架用户访问。
     * 不得返回密码、credentials 或完整 Authentication；不得递归读取 SecurityContext/SecurityUtil。
     * 每次读取框架当前用户都会调用，默认实现只映射内存数据；业务需要查库时自行控制缓存。
     */
    IUserDetails map(Authentication authentication);
}

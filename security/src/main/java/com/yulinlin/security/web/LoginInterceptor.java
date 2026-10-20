package com.yulinlin.security.web;

import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 可替换的登录拦截器。业务声明一个此类型的 Bean，框架自动注册并退让默认实现，
 * 无需自行编写 WebMvcConfigurer；普通业务 HandlerInterceptor 不参与此替换。
 * 多个实现需要通过 @Primary 明确选择。auth.enabled=false 会关闭自动注册，包括自定义实现。
 *
 * 自定义实现负责验证 Token 并设置 SecurityContext，框架统一保证请求开始、结束、
 * 异步切换以及 preHandle 拒绝或抛异常时清理上下文。
 * 仅需更换 Token 读取位置时，继承 LoginContextInterceptor 并覆盖 resolveToken 即可。
 */
public interface LoginInterceptor extends AsyncHandlerInterceptor {
}

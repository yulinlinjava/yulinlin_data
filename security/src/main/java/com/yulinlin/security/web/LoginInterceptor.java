package com.yulinlin.security.web;

import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 可替换的登录拦截器。业务声明一个此类型的 Bean，框架自动注册并退让默认实现，
 * 无需自行编写 WebMvcConfigurer；普通业务 HandlerInterceptor 不参与此替换。
 * 多个实现需要通过 @Primary 明确选择，不需要配置启用开关。
 *
 * 自定义实现负责验证 Token 并设置 SecurityContext，或配合自定义 ISecurityManager 使用外部身份上下文。
 * 框架统一保证请求开始、结束、异步切换以及 preHandle 拒绝或抛异常时清理 SecurityContext；
 * 外部框架自己的上下文由业务适配实现负责恢复和清理。
 * MVC 静态资源由统一注册层直接放行，不调用本接口的登录校验或后续生命周期回调；仍清理框架上下文。
 * 公开接口也应尝试解析携带的 Token，不要根据 ApiPublic 提前返回。
 * 用 LoginException 表示无效/过期凭证且不要提前写响应：统一注册层会对公开入口按匿名放行，
 * 对受保护入口返回 401。直接返回 false、已写入错误响应或其他异常不参与匿名降级。
 * 默认请求头名称和前缀通过 yulinlin.security.auth.token-header / token-prefix 配置。
 * 完全自定义实现自行决定读取规则；仅需读取 Cookie 等其他位置时，可继承 LoginContextInterceptor 覆盖 resolveToken。
 * 继承时必须把容器注入的 SecurityAuthProperties 传给父类构造，避免绕过 YAML 绑定。
 */
public interface LoginInterceptor extends AsyncHandlerInterceptor {
}

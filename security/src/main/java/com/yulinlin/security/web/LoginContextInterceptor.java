package com.yulinlin.security.web;

import com.yulinlin.security.SecurityAuthProperties;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Objects;
import java.util.function.Supplier;

/** 只恢复身份；凭证失败抛 LoginException，由统一注册层按入口策略决定匿名放行或返回 401。 */
public class LoginContextInterceptor implements LoginInterceptor {
    private final Supplier<ISecurityManager> managers;
    private final String tokenHeader;
    private final String tokenPrefix;

    /** Spring 接入时传入容器绑定的配置 Bean，不在拦截器内部另建默认配置。 */
    public LoginContextInterceptor(Supplier<ISecurityManager> managers, SecurityAuthProperties properties) {
        this.managers = Objects.requireNonNull(managers, "managers");
        Objects.requireNonNull(properties, "properties");
        this.tokenHeader = properties.getTokenHeader();
        this.tokenPrefix = properties.getTokenPrefix().isEmpty() ? "" : properties.getTokenPrefix() + " ";
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SecurityContext.clear();
        try {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            ISecurityManager manager = managers.get();
            // 只启用加密/限流的项目不接管其登录请求头。
            if (manager instanceof AnonymousSecurityManager) return true;
            String token = resolveToken(request);
            if (token == null) return true;
            if (token.isBlank() || token.length() > 8192
                    || token.chars().anyMatch(Character::isWhitespace)) throw unauthorized();
            LoginSession session = manager.resolveSession(token);
            if (session == null || session.expired() || !token.equals(session.token())) throw unauthorized();
            onSessionResolved(session);
            SecurityContext.set(session);
            return true;
        } catch (RuntimeException | Error error) {
            // preHandle 自身抛异常时，MVC 不保证回调本拦截器的 afterCompletion。
            SecurityContext.clear();
            throw error;
        }
    }

    /**
     * 只提取原始 Token，不在这里解析声明或信任用户 ID；签名与会话验证由管理器完成。
     * 返回 null 表示未携带 Token，返回空字符串会被视为无效 Token。
     * 默认读取 yulinlin.security.auth.token-header，按 token-prefix 剥离前缀。
     * 业务可在 @Bean 中返回本类子类，覆盖此方法读取自定义请求头或 Cookie。
     */
    protected String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(tokenHeader);
        if (header == null) return null;
        if (header.length() > 8192 || !header.regionMatches(true, 0, tokenPrefix, 0, tokenPrefix.length()))
            throw unauthorized();
        return header.substring(tokenPrefix.length()).trim();
    }

    /** 身份验证成功后供适配器同步外部请求上下文；不得在此信任未经验证的 Token。 */
    protected void onSessionResolved(LoginSession session) { }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        SecurityContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) {
        SecurityContext.clear();
    }

    private static LoginException unauthorized() {
        return new LoginException("登录已失效，请重新登录");
    }
}

package com.yulinlin.security.web;

import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.function.Supplier;

/** 只恢复身份，不重复处理权限注解；MVC 完成或切换异步线程时移除身份。 */
public class LoginContextInterceptor implements LoginInterceptor {
    private final Supplier<ISecurityManager> managers;

    public LoginContextInterceptor(Supplier<ISecurityManager> managers) {
        this.managers = Objects.requireNonNull(managers, "managers");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SecurityContext.clear();
        try {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            ISecurityManager manager = managers.get();
            // 只启用加密/限流的项目不接管其 Authorization。
            if (manager instanceof AnonymousSecurityManager) return true;
            String token = resolveToken(request);
            if (token == null) return true;
            if (token.isBlank() || token.length() > 8192
                    || token.chars().anyMatch(Character::isWhitespace)) throw unauthorized();
            LoginSession session = manager.resolveSession(token);
            if (session == null || session.expired() || !token.equals(session.token())) throw unauthorized();
            SecurityContext.set(session);
            return true;
        } catch (LoginException error) {
            SecurityContext.clear();
            throw unauthorized();
        } catch (RuntimeException | Error error) {
            // preHandle 自身抛异常时，MVC 不保证回调本拦截器的 afterCompletion。
            SecurityContext.clear();
            throw error;
        }
    }

    /**
     * 只提取原始 Token，不在这里解析声明或信任用户 ID；签名与会话验证由管理器完成。
     * 返回 null 表示未携带 Token，返回空字符串会被视为无效 Token。
     * 业务可在 @Bean 中返回本类子类，覆盖此方法读取自定义请求头或 Cookie。
     */
    protected String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) return null;
        if (header.length() > 8192 || !header.regionMatches(true, 0, "Bearer ", 0, 7))
            throw unauthorized();
        return header.substring(7).trim();
    }

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

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
    }
}

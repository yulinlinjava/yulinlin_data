package com.yulinlin.security.risk;

import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.risk.RiskModels.BehaviorEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import java.util.Map;
import java.util.Arrays;
import java.util.function.Supplier;

/** Checks parameter resources here; JSON resources are checked after conversion, before handler invocation. */
public final class RiskWebMvcConfigurer implements WebMvcConfigurer {
    static final String CHECKED = RiskWebMvcConfigurer.class.getName() + ".checked";
    private final RiskService service;
    private final Supplier<ISecurityManager> managers;
    public RiskWebMvcConfigurer(RiskService service, Supplier<ISecurityManager> managers) {
        this.service = service; this.managers = managers;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod method) || "OPTIONS".equals(request.getMethod())) return true;
                ApiRisk annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ApiRisk.class);
                if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ApiRisk.class);
                if (annotation == null || request.getAttribute(CHECKED) != null) return true;
                if (!annotation.resourceBody().isEmpty()) {
                    if (!annotation.resourceParam().isEmpty())
                        throw new IllegalStateException("@ApiRisk 的 resourceBody 与 resourceParam 不能同时配置");
                    long bodies = Arrays.stream(method.getMethodParameters()).filter(parameter ->
                            parameter.hasParameterAnnotation(RequestBody.class)
                                    || HttpEntity.class.isAssignableFrom(parameter.getParameterType())).count();
                    if (bodies != 1)
                        throw new IllegalStateException("@ApiRisk.resourceBody 需要一个 @RequestBody 或 HttpEntity 参数");
                    for (String part : annotation.resourceBody().split("\\.", -1))
                        if (part.isBlank()) throw new IllegalStateException("@ApiRisk.resourceBody 字段路径不能为空");
                    userId(); // Authenticate before reading the body; decision follows body conversion.
                    return true;
                }
                String resource = null;
                if (!annotation.resourceParam().isEmpty()) {
                    Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                    if (variables instanceof Map<?, ?> map && map.get(annotation.resourceParam()) != null)
                        resource = String.valueOf(map.get(annotation.resourceParam()));
                    else resource = request.getParameter(annotation.resourceParam());
                }
                check(request, response, annotation, resource);
                return true;
            }
        }).order(Ordered.HIGHEST_PRECEDENCE + 75);
    }

    void check(HttpServletRequest request, HttpServletResponse response, ApiRisk annotation, String resource) {
        if (request.getAttribute(CHECKED) != null) return;
        String userId = userId();
        BehaviorEvent event;
        try { event = new BehaviorEvent(userId, annotation.value(), resource); }
        catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行为参数无效", error); }
        var decision = service.checkAndRecord(event);
        if (!decision.allowed()) {
            if (response != null) response.setHeader("Retry-After", "60");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "访问暂时受限，请稍后再试");
        }
        request.setAttribute(CHECKED, Boolean.TRUE);
    }

    private String userId() {
        var user = managers.get().getUserDetails();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return user.getUserId();
    }
}


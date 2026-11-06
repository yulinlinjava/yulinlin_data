package com.yulinlin.security.risk;

import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.risk.RiskModels.BehaviorEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
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
    static final String PENDING_BODY = RiskWebMvcConfigurer.class.getName() + ".pendingBody";
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
                String resource = null;
                if (!annotation.resource().isEmpty()) {
                    for (String part : annotation.resource().split("\\.", -1))
                        if (part.isBlank()) throw new IllegalStateException("@ApiRisk.resource 字段路径不能为空");
                    Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                    if (variables instanceof Map<?, ?> map && map.get(annotation.resource()) != null)
                        resource = String.valueOf(map.get(annotation.resource()));
                    else resource = request.getParameter(annotation.resource());
                    if (resource == null && isJson(request.getContentType())) {
                        long bodies = Arrays.stream(method.getMethodParameters())
                                .filter(RiskWebMvcConfigurer::isBodyParameter).count();
                        if (bodies > 1) throw new IllegalStateException("@ApiRisk 只支持一个请求体参数");
                        if (bodies == 1) {
                            userId(); // Authenticate now; resolve the resource from the converted body later.
                            request.setAttribute(PENDING_BODY, annotation);
                            return true;
                        }
                    }
                }
                check(request, annotation, resource);
                return true;
            }
        }).order(Ordered.HIGHEST_PRECEDENCE + 75);
    }

    void check(HttpServletRequest request, ApiRisk annotation, String resource) {
        if (request.getAttribute(CHECKED) != null) return;
        String userId = userId();
        BehaviorEvent event;
        try { event = new BehaviorEvent(userId, annotation.value(), resource); }
        catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行为参数无效", error); }
        var decision = service.checkAndRecord(event);
        if (!decision.allowed()) throw new ApiRiskException(event, decision);
        request.setAttribute(CHECKED, Boolean.TRUE);
        request.removeAttribute(PENDING_BODY);
    }

    static boolean isBodyParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestBody.class)
                || HttpEntity.class.isAssignableFrom(parameter.getParameterType());
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) return false;
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(type) || type.getSubtype().endsWith("+json");
        } catch (IllegalArgumentException ignored) {
            return false; // MVC remains responsible for rejecting an invalid Content-Type.
        }
    }

    private String userId() {
        var user = managers.get().getUserDetails();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return user.getUserId();
    }
}


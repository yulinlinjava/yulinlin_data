package com.yulinlin.security.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.lang.reflect.Type;

/** Reuses the converted body; never consumes or replaces the servlet input stream. */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnBean(RiskWebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "yulinlin.security.risk", name = "enabled", havingValue = "true")
public final class RiskRequestBodyAdvice extends RequestBodyAdviceAdapter {
    private final RiskWebMvcConfigurer checks;
    private final ObjectMapper mapper;

    public RiskRequestBodyAdvice(RiskWebMvcConfigurer checks, ObjectMapper mapper) {
        this.checks = checks;
        this.mapper = mapper;
    }

    @Override public boolean supports(MethodParameter parameter, Type type,
                                      Class<? extends HttpMessageConverter<?>> converter) {
        ApiRisk risk = annotation(parameter);
        return risk != null && !risk.resourceBody().isEmpty();
    }

    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
                                          Type type, Class<? extends HttpMessageConverter<?>> converter) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        if (attributes.getRequest().getAttribute(RiskWebMvcConfigurer.CHECKED) != null) return body;
        MediaType contentType = input.getHeaders().getContentType();
        if (contentType == null || !(MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || contentType.getSubtype().endsWith("+json")))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "resourceBody 需要 JSON 请求体");
        ApiRisk risk = annotation(parameter);
        JsonNode node;
        try {
            if (body instanceof String text) node = mapper.readTree(text);
            else if (body instanceof byte[] bytes) node = mapper.readTree(bytes);
            else node = mapper.valueToTree(body);
        } catch (IOException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取风控 JSON 字段", error);
        }
        if (node == null || !node.isObject())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceBody 需要 JSON 对象");
        for (String part : risk.resourceBody().split("\\.")) node = node.path(part);
        String resource = null;
        if (!node.isMissingNode() && !node.isNull()) {
            if (!node.isTextual() && !node.isNumber())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源 ID 必须为字符串或数字");
            resource = node.asText();
        }
        checks.check(attributes.getRequest(), attributes.getResponse(), risk, resource);
        return body;
    }

    @Override public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter,
                                           Type type, Class<? extends HttpMessageConverter<?>> converter) {
        // Even @RequestBody(required=false) must not bypass the risk check.
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceBody 需要非空 JSON 请求体");
    }

    private static ApiRisk annotation(MethodParameter parameter) {
        ApiRisk risk = parameter.getMethodAnnotation(ApiRisk.class);
        return risk != null ? risk : AnnotatedElementUtils.findMergedAnnotation(parameter.getContainingClass(), ApiRisk.class);
    }
}

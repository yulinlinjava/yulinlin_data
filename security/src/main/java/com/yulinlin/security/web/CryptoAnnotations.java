package com.yulinlin.security.web;

import com.yulinlin.security.annotation.ApiCrypto;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;

final class CryptoAnnotations {

    private CryptoAnnotations() {
    }

    static ApiCrypto find(MethodParameter parameter) {
        if (parameter.getMethod() != null) {
            ApiCrypto methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                    parameter.getMethod(), ApiCrypto.class);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
        }
        return AnnotatedElementUtils.findMergedAnnotation(parameter.getContainingClass(), ApiCrypto.class);
    }
}

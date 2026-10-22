package com.yulinlin.security.web;

import com.yulinlin.security.annotation.ApiCrypto;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;

final class CryptoAnnotations {

    private CryptoAnnotations() {
    }

    /** 全局开启时默认严格加密；方法/类上的 false 为例外，方法配置优先。 */
    static boolean encryptionRequired(MethodParameter parameter, boolean enabled) {
        if (!enabled) return false;
        ApiCrypto annotation = find(parameter);
        return annotation == null || annotation.value();
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

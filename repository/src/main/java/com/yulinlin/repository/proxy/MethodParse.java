package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.session.RequestType;

import java.lang.reflect.Method;

/**
 * 方法解析
 */
public interface MethodParse {

    Object apply(String name, Object[] args, Method method, Object obj);

    boolean support(String name);

    RequestType requestType();

    /** Validate method-level configuration when the Repository proxy is created. */
    default void validate(Method method) {
    }

    /** Repository-aware validation while retaining the original extension API. */
    default void validate(Class<?> repositoryType, Method method) {
        validate(method);
    }

}

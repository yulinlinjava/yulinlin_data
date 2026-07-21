package com.yulinlin.repository.proxy;

import java.lang.reflect.Method;

/**
 * 方法解析
 */
public interface MethodParse {

    Object apply(String name,Object[] args,Method method,Object obj);

    boolean support(String name);

}

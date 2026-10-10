package com.yulinlin.repository.session;

import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.repository.proxy.MethodParseManager;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RepositorySession {

    private final ConcurrentMap<Class<?>, Object> cache = new ConcurrentHashMap<>();

    private final MethodParseManager methodParseManager;

    public RepositorySession(MethodParseManager methodParseManager) {
        this.methodParseManager = methodParseManager;
    }

    public <E> E create(Class<E> key){
        return key.cast(cache.computeIfAbsent(key, type -> {
            methodParseManager.validate(type);
            return new Proxy(type).getProxyInstance();
        }));
    }

    /**
     * 字段代理
     */
    private class Proxy implements MethodInterceptor {

        private Class target;




        public Proxy(Class target) {
            this.target = target;
        }

        @Override
        public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
            boolean isAbstract =  Modifier.isAbstract(method.getModifiers());
           if(method.isDefault() || !isAbstract ){
              // return methodProxy.invokeSuper(o,objects);
                return ReflectionUtil.invokeMethod(o,method,objects);
            }
            return methodParseManager.apply(method.getName(),objects,method,o);


        }

        public Object getProxyInstance() {
            Class clazz = ProxyUtil.getProxyClass( target);

            Object o =  ProxyUtil.getProxyInstance(clazz,this);



            return  o;
        }
    }
}

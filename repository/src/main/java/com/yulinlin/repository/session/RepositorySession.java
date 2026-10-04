package com.yulinlin.repository.session;

import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.repository.proxy.MethodParseManager;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class RepositorySession {

    private Map<Class,Object> cache = new HashMap<>();

    private MethodParseManager methodParseManager;

    public RepositorySession(MethodParseManager methodParseManager) {
        this.methodParseManager = methodParseManager;
    }

    public <E> E create(Class<E> key){
        Object val =  cache.get(key);
        if(val == null){
            val =new Proxy(key).getProxyInstance();
            cache.put(key,val);
        }
        return (E)val;
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

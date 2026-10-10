package com.yulinlin.repository.session;

import com.yulinlin.repository.proxy.MethodParseManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
            if (!type.isInterface()) {
                throw new IllegalArgumentException("Repository type must be an interface: " + type.getName());
            }
            methodParseManager.validate(type);
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    new RepositoryInvocationHandler(type));
        }));
    }

    private final class RepositoryInvocationHandler implements InvocationHandler {
        private final Class<?> repositoryType;

        private RepositoryInvocationHandler(Class<?> repositoryType) {
            this.repositoryType = repositoryType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object[] values = args == null ? new Object[0] : args;
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == values[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> repositoryType.getName() + " proxy";
                    default -> throw new IllegalStateException("Unsupported Object method: " + method);
                };
            }
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, values);
            if (!Modifier.isAbstract(method.getModifiers())) {
                throw new IllegalStateException("Unsupported Repository method: " + method.toGenericString());
            }
            return methodParseManager.apply(method.getName(), values, method, proxy);
        }
    }
}

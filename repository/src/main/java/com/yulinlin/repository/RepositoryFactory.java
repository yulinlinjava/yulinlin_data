package com.yulinlin.repository;

import com.yulinlin.repository.session.RepositorySession;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ClassUtils;

import java.util.Objects;

public class RepositoryFactory implements FactoryBean<Object>, BeanClassLoaderAware {

    private final String markerInterfaceName;
    private volatile Class<?> markerInterface;
    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    private RepositorySession repositorySession;

    public RepositoryFactory(Class<?> markerInterface) {
        this.markerInterface = Objects.requireNonNull(markerInterface, "markerInterface");
        this.markerInterfaceName = markerInterface.getName();
    }

    public RepositoryFactory(String markerInterface) {
        this.markerInterfaceName = Objects.requireNonNull(markerInterface, "markerInterface").trim();
        if (this.markerInterfaceName.isEmpty()) {
            throw new IllegalArgumentException("markerInterface must not be blank");
        }
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Autowired
    public void setRepositorySession(RepositorySession repositorySession) {
        this.repositorySession = repositorySession;
    }

    @Override
    public Object getObject() {
        return repositorySession.create(resolveRepositoryType());
    }

    @Override
    public Class<?> getObjectType() {
        return resolveRepositoryType();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private Class<?> resolveRepositoryType() {
        Class<?> resolved = markerInterface;
        if (resolved == null) {
            resolved = ClassUtils.resolveClassName(markerInterfaceName, beanClassLoader);
            markerInterface = resolved;
        }
        return resolved;
    }
}

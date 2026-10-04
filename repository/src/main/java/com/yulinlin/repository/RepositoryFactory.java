package com.yulinlin.repository;

import com.yulinlin.repository.session.RepositorySession;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

public class RepositoryFactory implements FactoryBean {

    private Class<?> markerInterface;

    @Autowired
    private RepositorySession repositoryFactory;

    public RepositoryFactory(Class<?> markerInterface) {
        this.markerInterface = markerInterface;
    }

    public void setRepositoryFactory(RepositorySession repositoryFactory) {
        this.repositoryFactory = repositoryFactory;
    }

    @Override
    public Object getObject() throws Exception {
        return repositoryFactory.create(markerInterface);
    }

    @Override
    public Class<?> getObjectType() {
        return markerInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}

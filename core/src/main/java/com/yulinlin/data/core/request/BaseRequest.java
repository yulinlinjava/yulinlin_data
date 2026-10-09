package com.yulinlin.data.core.request;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import com.yulinlin.data.core.session.RequestType;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class BaseRequest<E> {


    //对序列化使用
    private Class<?> entityClass;

    //标记来自那个实体类
    private Class<?> fromClass;

    private boolean cache;

    /** Null means use yulinlin.cache.ttl. */
    private Duration cacheTtl;

    private final Set<String> cacheNamespaces = new LinkedHashSet<>();

    private String session;

    private JoinCluster cluster;

    private Object root;


    private boolean sessionChange;



    public BaseRequest(Class entityClass) {
        this(entityClass,entityClass);
    }

    public BaseRequest(Class entityClass, Class fromClass) {


        this.entityClass = entityClass;
        this.fromClass = fromClass;

    }


    public boolean isSessionChange() {
        return sessionChange;
    }

    public void setSession(String session) {
        if(this.session != null && !session.equals(this.session)){
            sessionChange=true;
        }
        this.session = session;

    }

    public void setCluster(JoinCluster cluster) {
        this.cluster = cluster;
    }

    public boolean isCache() {
        return cache;
    }

    public void setCache(boolean cache) {
        this.cache = cache;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl == null ? null
                : QueryCacheProperties.requirePositive(cacheTtl, "cache ttl");
    }

    public void addCacheNamespaces(String... resources) {
        if (resources == null) return;
        for (String resource : resources) {
            if (resource == null || resource.isBlank()) {
                throw new IllegalArgumentException("cache namespace must not be blank");
            }
            cacheNamespaces.add(resource.trim());
        }
    }

    public Set<String> getCacheNamespaces() {
        return Collections.unmodifiableSet(cacheNamespaces);
    }

    public String getSession() {
        return session;
    }

    public JoinCluster getCluster() {
        return cluster;
    }

    public Class<?> getFromClass() {
        return fromClass;
    }

    public void setFromClass(Class fromClass) {
        this.fromClass = fromClass;
    }


    public void setEntityClass(Class entityClass) {
        this.entityClass = entityClass;
    }


    public Object getRoot() {
        return root;
    }

    public void setRoot(Object root) {
        this.root = root;
    }

    public Class getEntityClass() {
        return entityClass;
    }

}

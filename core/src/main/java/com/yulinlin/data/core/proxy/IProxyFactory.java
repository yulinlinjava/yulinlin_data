package com.yulinlin.data.core.proxy;

import com.yulinlin.data.core.session.EntitySession;

import java.util.List;

public interface IProxyFactory {

    /**
     * 得到一个代理
     * @param data
     * @return
     */
    <E> E getProxy(E data);

    <E> List<E> getProxyList(List<E> data);
}

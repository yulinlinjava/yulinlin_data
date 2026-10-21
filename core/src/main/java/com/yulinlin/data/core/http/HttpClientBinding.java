package com.yulinlin.data.core.http;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.Objects;

/** Spring 单例初始化完成后绑定静态入口，销毁时只解除自己的绑定。 */
public final class HttpClientBinding implements SmartInitializingSingleton, DisposableBean {
    private final ObjectProvider<HttpRequestClient> clients;

    public HttpClientBinding(ObjectProvider<HttpRequestClient> clients) {
        this.clients = Objects.requireNonNull(clients, "clients must not be null");
    }

    @Override
    public void afterSingletonsInstantiated() {
        HttpRequestClient selected;
        try {
            selected = clients.getObject();
        } catch (NoUniqueBeanDefinitionException error) {
            throw new IllegalStateException("存在多个 HttpRequestClient，请通过 @Primary 明确选择默认 HTTP 客户端", error);
        }
        HttpUtil.bind(this, selected);
    }

    @Override
    public void destroy() { HttpUtil.unbind(this); }
}

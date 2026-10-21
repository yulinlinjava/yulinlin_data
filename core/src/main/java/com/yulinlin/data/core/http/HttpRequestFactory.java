package com.yulinlin.data.core.http;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/** 一个客户端共享一个连接池；单次超时只作用于当前线程创建的请求。 */
final class HttpRequestFactory implements ClientHttpRequestFactory, AutoCloseable {

    private static final ThreadFactory BODY_THREADS = Thread.ofVirtual().name("yulinlin-http-write-", 0).factory();
    private static final Executor BODY_WRITER = task -> BODY_THREADS.newThread(task).start();

    private final HttpClient httpClient;
    private final boolean owned;
    private final Duration defaultTimeout;
    private final ThreadLocal<Duration> requestTimeout = new ThreadLocal<>();

    HttpRequestFactory(Duration timeout, HttpClient httpClient) {
        this.defaultTimeout = requirePositiveTimeout(timeout);
        this.owned = httpClient == null;
        this.httpClient = owned ? HttpClient.newBuilder().connectTimeout(timeout).build() : httpClient;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod method) throws IOException {
        // 工厂是轻量请求适配器，不创建连接池，也不修改共享工厂的可变超时。
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient, BODY_WRITER);
        Duration timeout = requestTimeout.get();
        factory.setReadTimeout(timeout == null ? defaultTimeout : timeout);
        return factory.createRequest(uri, method);
    }

    <T> T withTimeout(Duration timeout, Supplier<T> operation) {
        Duration previous = requestTimeout.get();
        if (timeout == null) requestTimeout.remove();
        else requestTimeout.set(requirePositiveTimeout(timeout));
        try {
            return operation.get();
        } finally {
            if (previous == null) requestTimeout.remove();
            else requestTimeout.set(previous);
        }
    }

    static Duration requirePositiveTimeout(Duration timeout) {
        return HttpRequestData.requirePositiveTimeout(timeout);
    }

    @Override
    public void close() {
        if (owned) httpClient.shutdown();
    }
}

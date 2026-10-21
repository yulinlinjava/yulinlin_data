package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Convenient static HTTP entry point for simple synchronous requests.
 * <p>
 * Spring 单例初始化完成后，与注入的默认 HttpRequestClient 使用同一实例、执行器和配置。
 * 初始化阶段的业务应直接注入客户端；无 Spring 时延迟创建默认客户端。
 */
public final class HttpUtil {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static volatile HttpRequestClient defaultClient;
    private static volatile HttpRequestClient springClient;
    private static volatile boolean springStopped;
    private static Object springOwner;

    private static final class DefaultClient {
        private static final HttpRequestClient INSTANCE =
                HttpRequestClient.withTimeout(RestClient.builder(), new ObjectMapper(), DEFAULT_TIMEOUT);
    }

    private static HttpRequestClient client() {
        HttpRequestClient managed = springClient;
        if (managed != null) return managed;
        if (springStopped) throw new IllegalStateException("Spring HTTP 客户端已关闭，不再自动切回默认执行器");
        HttpRequestClient configured = defaultClient;
        return configured == null ? DefaultClient.INSTANCE : configured;
    }

    /** Spring 初始化完成后与注入的默认 HttpRequestClient 为同一个实例。 */
    public static HttpRequestClient getClient() { return client(); }

    private HttpUtil() {
    }

    public static synchronized void setClient(HttpRequestClient defaultClient) {
        Objects.requireNonNull(defaultClient, "client must not be null");
        if (springClient != null) {
            if (springClient != defaultClient) {
                throw new IllegalStateException("Spring 已绑定 HTTP 客户端，请通过 HttpExecutor 或 HttpRequestClient Bean 替换，不能只修改静态入口");
            }
            return;
        }
        HttpUtil.defaultClient = defaultClient;
        springStopped = false;
    }

    static synchronized void bind(Object owner, HttpRequestClient client) {
        if (springOwner != null && springOwner != owner) {
            throw new IllegalStateException("HttpUtil 已绑定其他应用上下文；多上下文应用请注入 HttpRequestClient 并自行管理静态绑定");
        }
        springOwner = owner;
        springClient = client;
        springStopped = false;
    }

    static synchronized void unbind(Object owner) {
        if (springOwner == owner) {
            springStopped = true;
            springClient = null;
            springOwner = null;
        }
    }

    public static HttpRequest get(String url) {
        return client().get(url);
    }

    public static HttpRequest head(String url) { return client().head(url); }

    public static HttpRequest post(String url) {
        return client().post(url);
    }

    public static HttpRequest put(String url) {
        return client().put(url);
    }

    public static HttpRequest patch(String url) {
        return client().patch(url);
    }

    public static HttpRequest delete(String url) {
        return client().delete(url);
    }

    public static HttpRequest request(HttpMethod method, String url) {
        return client().request(method, url);
    }

    /**
     * 返回当前客户端的轻量超时视图，继续复用选定的执行器，不绕过用户自定义实现。
     */
    public static HttpRequestClient withTimeout(Duration timeout) {
        return client().withTimeout(timeout);
    }

    public static boolean isNotFound(String url) {
        return client().isNotFound(url);
    }

    public static HttpResponse download(String url, Path destination) {
        return get(url).downloadTo(destination);
    }
}

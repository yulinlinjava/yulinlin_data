package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** 线程安全的客户端门面。请求组装、默认配置和 404 策略统一，网络实现委托 HttpExecutor。 */
public final class HttpRequestClient implements AutoCloseable {
    private final HttpExecutor executor;
    private final boolean ownsExecutor;
    private final ObjectMapper objectMapper;
    private final Duration defaultTimeout;
    private final boolean defaultTimeoutOverridden;
    private final int maxResponseSize;
    private final int maxErrorSize;

    /** 外部执行器由其所有者管理；Spring Bean 会由容器关闭。 */
    public HttpRequestClient(HttpExecutor executor) {
        this(executor, new ObjectMapper(), new HttpRequestProperties());
    }

    public HttpRequestClient(HttpExecutor executor, ObjectMapper mapper) {
        this(executor, mapper, new HttpRequestProperties());
    }

    public HttpRequestClient(HttpExecutor executor, ObjectMapper mapper, HttpRequestProperties properties) {
        this(executor, mapper, properties, false);
    }

    /** 兼容已有写法：完整保留外部 RestClient 的网络设置。 */
    public HttpRequestClient(RestClient restClient, ObjectMapper mapper) {
        this(restClient, mapper, new HttpRequestProperties());
    }

    /** properties 的 timeout 不覆盖外部 RestClient，仅作为快照的默认值。 */
    public HttpRequestClient(RestClient restClient, ObjectMapper mapper, HttpRequestProperties properties) {
        this(new RestClientHttpExecutor(restClient, mapper), mapper, properties, true);
    }

    private HttpRequestClient(HttpExecutor executor, ObjectMapper mapper,
                              HttpRequestProperties properties, boolean ownsExecutor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.objectMapper = Objects.requireNonNull(mapper, "objectMapper must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        this.defaultTimeout = HttpRequestData.requirePositiveTimeout(properties.getTimeout());
        this.defaultTimeoutOverridden = false;
        this.maxResponseSize = size(properties.getMaxResponseSize(), "max-response-size");
        this.maxErrorSize = size(properties.getMaxErrorSize(), "max-error-size");
        this.ownsExecutor = ownsExecutor;
    }

    private HttpRequestClient(HttpRequestClient source, Duration timeout) {
        this.executor = source.executor;
        this.ownsExecutor = false;
        this.objectMapper = source.objectMapper;
        this.defaultTimeout = HttpRequestData.requirePositiveTimeout(timeout);
        this.defaultTimeoutOverridden = true;
        this.maxResponseSize = source.maxResponseSize;
        this.maxErrorSize = source.maxErrorSize;
    }

    /** 创建只改变默认响应超时的轻量视图，仍使用同一个执行器；关闭视图不关闭共享执行器。 */
    public HttpRequestClient withTimeout(Duration timeout) { return new HttpRequestClient(this, timeout); }

    public static HttpRequestClient withTimeout(RestClient.Builder builder, ObjectMapper mapper, Duration timeout) {
        HttpRequestProperties properties = new HttpRequestProperties();
        properties.setTimeout(timeout);
        return create(builder, mapper, properties);
    }

    public static HttpRequestClient create(RestClient.Builder builder, ObjectMapper mapper,
                                          HttpRequestProperties properties) {
        return create(builder, mapper, properties, null);
    }

    /** 显式创建默认执行器，不查找 Spring Bean；transport 生命周期由调用方负责。 */
    public static HttpRequestClient create(RestClient.Builder builder, ObjectMapper mapper,
                                          HttpRequestProperties properties, HttpClient transport) {
        RestClientHttpExecutor executor = RestClientHttpExecutor.create(builder, mapper, properties, transport);
        try {
            return new HttpRequestClient(executor, mapper, properties, true);
        } catch (RuntimeException | Error error) {
            executor.close();
            throw error;
        }
    }

    public HttpExecutor getExecutor() { return executor; }
    public HttpRequest get(String url) { return request(HttpMethod.GET, url); }
    public HttpRequest head(String url) { return request(HttpMethod.HEAD, url); }
    public HttpRequest post(String url) { return request(HttpMethod.POST, url); }
    public HttpRequest put(String url) { return request(HttpMethod.PUT, url); }
    public HttpRequest patch(String url) { return request(HttpMethod.PATCH, url); }
    public HttpRequest delete(String url) { return request(HttpMethod.DELETE, url); }

    public HttpRequest request(HttpMethod method, String url) {
        return new HttpRequest(this, method, url);
    }

    /** 统一执行 HEAD；405/501 回退 GET。只有明确 404 返回 true，false 不代表健康。 */
    public boolean isNotFound(String url) {
        try {
            int status = head(url).status();
            if (status == 405 || status == 501) status = get(url).status();
            return status == 404;
        } catch (HttpRequestException exception) {
            return Integer.valueOf(404).equals(exception.getStatusCode());
        }
    }

    HttpRequestData snapshot(HttpMethod method, URI uri, HttpHeaders headers, HttpRequestData.BodyKind kind,
                             Object body, MediaType type, MultiValueMap<String, String> form,
                             MultiValueMap<String, Object> multipart, Duration timeout) {
        return new HttpRequestData(method, uri, headers, kind, body, type, form, multipart,
                timeout == null ? defaultTimeout : timeout, timeout != null || defaultTimeoutOverridden,
                maxResponseSize, maxErrorSize);
    }

    HttpResponse execute(HttpRequestData data) {
        HttpResponse response = Objects.requireNonNull(executor.execute(data), "HttpExecutor.execute 返回值不能为 null");
        // 防御性检查统一公开语义；执行器仍必须在读流阶段限制内存，不能只依赖这里。
        if (response.getStatusCode().isError()) throw responseError(data, response);
        if (response.bodyLength() > data.getMaxResponseSize()) {
            throw new HttpRequestException(data.getMethod().name(), data.getUri().toString(), response.getStatus(), null,
                    new IOException("执行器返回的正文超过 max-response-size"));
        }
        return response.withObjectMapper(objectMapper);
    }

    int status(HttpRequestData data) { return executor.status(data); }

    HttpResponse download(HttpRequestData data, Path destination) {
        HttpResponse response = Objects.requireNonNull(executor.download(data, destination), "HttpExecutor.download 返回值不能为 null");
        if (!response.isSuccessful()) throw responseError(data, response);
        return response.withObjectMapper(objectMapper);
    }

    private HttpRequestException responseError(HttpRequestData data, HttpResponse response) {
        return new HttpRequestException(data.getMethod().name(), data.getUri().toString(), response.getStatus(),
                response.bodyPrefix(data.getMaxErrorSize()), response.bodyLength() > data.getMaxErrorSize(), null);
    }

    private static int size(DataSize value, String name) {
        long bytes = Objects.requireNonNull(value, name + " must not be null").toBytes();
        if (bytes <= 0 || bytes >= Integer.MAX_VALUE) throw new IllegalArgumentException(name + " 必须为正数且小于 2GB");
        return (int) bytes;
    }

    /** 只关闭本客户端通过工厂创建的执行器；注入的共享执行器由其所有者管理。 */
    @Override
    public void close() {
        if (ownsExecutor) executor.close();
    }
}

package com.yulinlin.data.core.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/**
 * 执行时的请求快照。头、表单及 multipart 容器为只读副本；正文对象和文件不深度克隆。
 * multipart 的值只会是 String 或 HttpFile，输入流文件仍是一次性资源。
 */
public final class HttpRequestData {
    public enum BodyKind { NONE, JSON, FORM, MULTIPART, RAW }

    private final HttpMethod method;
    private final URI uri;
    private final HttpHeaders headers;
    private final BodyKind bodyKind;
    private final Object body;
    private final MediaType contentType;
    private final MultiValueMap<String, String> form;
    private final MultiValueMap<String, Object> multipart;
    private final Duration timeout;
    private final boolean timeoutOverridden;
    private final int maxResponseSize;
    private final int maxErrorSize;

    HttpRequestData(HttpMethod method, URI uri, HttpHeaders headers, BodyKind bodyKind, Object body,
                    MediaType contentType, MultiValueMap<String, String> form,
                    MultiValueMap<String, Object> multipart, Duration timeout, boolean timeoutOverridden,
                    int maxResponseSize, int maxErrorSize) {
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.uri = Objects.requireNonNull(uri, "uri must not be null");
        HttpHeaders copied = new HttpHeaders();
        headers.forEach((name, values) -> copied.put(name, new ArrayList<>(values)));
        this.headers = HttpHeaders.readOnlyHttpHeaders(copied);
        this.bodyKind = Objects.requireNonNull(bodyKind, "bodyKind must not be null");
        this.body = body;
        this.contentType = contentType;
        this.form = snapshot(form);
        this.multipart = snapshot(multipart);
        this.timeout = requirePositiveTimeout(timeout);
        this.timeoutOverridden = timeoutOverridden;
        this.maxResponseSize = maxResponseSize;
        this.maxErrorSize = maxErrorSize;
    }

    private static <T> MultiValueMap<String, T> snapshot(MultiValueMap<String, T> source) {
        MultiValueMap<String, T> copy = new LinkedMultiValueMap<>(source.size());
        source.forEach((name, values) -> copy.put(name, Collections.unmodifiableList(new ArrayList<>(values))));
        return CollectionUtils.unmodifiableMultiValueMap(copy);
    }

    static Duration requirePositiveTimeout(Duration timeout) {
        Duration value = Objects.requireNonNull(timeout, "timeout must not be null");
        if (value.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("timeout 必须至少为 1 毫秒");
        }
        value.toMillis();
        return value;
    }

    public HttpMethod getMethod() { return method; }
    public URI getUri() { return uri; }
    public HttpHeaders getHeaders() { return headers; }
    public BodyKind getBodyKind() { return bodyKind; }
    public Object getBody() { return body; }
    public MediaType getContentType() { return contentType; }
    public MultiValueMap<String, String> getForm() { return form; }
    public MultiValueMap<String, Object> getMultipart() { return multipart; }
    public Duration getTimeout() { return timeout; }
    /** 区分显式单次超时与默认值，供包装外部已有客户端的适配器安全判断。 */
    public boolean isTimeoutOverridden() { return timeoutOverridden; }
    /** 字节数。 */
    public int getMaxResponseSize() { return maxResponseSize; }
    /** 字节数。 */
    public int getMaxErrorSize() { return maxErrorSize; }
}

package com.yulinlin.data.core.http;

import com.yulinlin.data.core.http.HttpRequestData.BodyKind;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** 可变链式请求构造器，不跨线程共享；执行时生成请求快照，底层由 HttpExecutor 决定。 */
public final class HttpRequest {
    private final HttpRequestClient client;
    private final HttpMethod method;
    private final String url;
    private final HttpHeaders headers = new HttpHeaders();
    private final MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    private final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    private final MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();

    private BodyKind bodyKind = BodyKind.NONE;
    private Object body;
    private MediaType rawContentType;
    private Duration timeout;

    HttpRequest(HttpRequestClient client, HttpMethod method, String url) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.url = Objects.requireNonNull(url, "url must not be null");
    }

    public HttpRequest query(String name, Object value) {
        if (value != null) query.add(Objects.requireNonNull(name, "query name must not be null"), String.valueOf(value));
        return this;
    }

    public HttpRequest header(String name, String value) {
        headers.add(name, value);
        return this;
    }

    public HttpRequest headers(HttpHeaders headers) {
        this.headers.addAll(headers);
        return this;
    }

    public HttpRequest bearerToken(String token) {
        headers.setBearerAuth(token);
        return this;
    }

    public HttpRequest basicAuth(String username, String password) {
        headers.setBasicAuth(username, password);
        return this;
    }

    public HttpRequest json(Object value) {
        switchBodyKind(BodyKind.JSON);
        body = Objects.requireNonNull(value, "JSON body must not be null");
        return this;
    }

    public HttpRequest body(Object value, MediaType contentType) {
        switchBodyKind(BodyKind.RAW);
        body = Objects.requireNonNull(value, "body must not be null");
        rawContentType = Objects.requireNonNull(contentType, "contentType must not be null");
        return this;
    }

    public HttpRequest form(String name, Object value) {
        switchBodyKind(BodyKind.FORM);
        form.add(Objects.requireNonNull(name, "form name must not be null"), value == null ? "" : String.valueOf(value));
        return this;
    }

    public HttpRequest form(Map<String, Object> data) {
        Objects.requireNonNull(data, "form must not be null").forEach(this::form);
        return this;
    }

    public HttpRequest multipart(String name, Object value) {
        switchBodyKind(BodyKind.MULTIPART);
        multipart.add(Objects.requireNonNull(name, "part name must not be null"), value == null ? "" : String.valueOf(value));
        return this;
    }

    public HttpRequest file(String name, HttpFile file) {
        switchBodyKind(BodyKind.MULTIPART);
        multipart.add(Objects.requireNonNull(name, "part name must not be null"),
                Objects.requireNonNull(file, "file must not be null"));
        return this;
    }

    /** 设置本次响应等待与读取超时，不修改客户端默认值。 */
    public HttpRequest timeout(Duration timeout) {
        this.timeout = HttpRequestData.requirePositiveTimeout(timeout);
        return this;
    }

    public HttpResponse execute() { return client.execute(toRequestData()); }

    /** 包括 4xx/5xx；网络错误仍抛出异常，不读取完整正文。 */
    public int status() { return client.status(toRequestData()); }

    public HttpResponse downloadTo(Path destination) {
        Path target = Objects.requireNonNull(destination, "destination must not be null").toAbsolutePath().normalize();
        return client.download(toRequestData(), target);
    }

    /** 只创建快照，不发请求。正文对象及文件流不深度复制。 */
    public HttpRequestData toRequestData() {
        MediaType contentType = switch (bodyKind) {
            case NONE -> null;
            case JSON -> MediaType.APPLICATION_JSON;
            case FORM -> MediaType.APPLICATION_FORM_URLENCODED;
            case MULTIPART -> MediaType.MULTIPART_FORM_DATA;
            case RAW -> rawContentType;
        };
        return client.snapshot(method, HttpRequestUri.create(url, query), headers, bodyKind, body,
                contentType, form, multipart, timeout);
    }

    private void switchBodyKind(BodyKind requestedBodyKind) {
        if (bodyKind != BodyKind.NONE && bodyKind != requestedBodyKind) {
            throw new IllegalStateException("A request can have only one body type");
        }
        bodyKind = requestedBodyKind;
    }
}

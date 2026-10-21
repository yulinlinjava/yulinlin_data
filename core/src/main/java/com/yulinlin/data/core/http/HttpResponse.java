package com.yulinlin.data.core.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Arrays;

/**
 * Immutable HTTP response with convenient text and JSON accessors.
 */
public final class HttpResponse {

    private final HttpStatusCode statusCode;
    private final HttpHeaders headers;
    private final byte[] body;
    private final ObjectMapper objectMapper;

    private static final class DefaultMapper {
        private static final ObjectMapper INSTANCE = new ObjectMapper();
    }

    /** 自定义执行器的公开响应入口，防御性复制头与正文；客户端会统一关联其 ObjectMapper。 */
    public static HttpResponse of(int status, HttpHeaders headers, byte[] body) {
        return of(status, headers, body, DefaultMapper.INSTANCE);
    }

    public static HttpResponse of(int status, HttpHeaders headers, byte[] body, ObjectMapper mapper) {
        return new HttpResponse(HttpStatusCode.valueOf(status), headers, body == null ? null : body.clone(), mapper);
    }

    private HttpResponse(HttpResponse response, ObjectMapper mapper) {
        this.statusCode = response.statusCode;
        this.headers = response.headers;
        this.body = response.body;
        this.objectMapper = mapper;
    }

    HttpResponse withObjectMapper(ObjectMapper mapper) {
        return mapper == objectMapper ? this : new HttpResponse(this, mapper);
    }

    int bodyLength() { return body.length; }

    byte[] bodyPrefix(int size) { return Arrays.copyOf(body, Math.min(body.length, size)); }

    HttpResponse(HttpStatusCode statusCode, HttpHeaders headers, byte[] body, ObjectMapper objectMapper) {
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode must not be null");
        HttpHeaders copiedHeaders = new HttpHeaders();
        Objects.requireNonNull(headers, "headers must not be null")
                .forEach((name, values) -> copiedHeaders.put(name, new java.util.ArrayList<>(values)));
        this.headers = HttpHeaders.readOnlyHttpHeaders(copiedHeaders);
        // 包内调用仅传入本次新读取、未向外暴露的数组，转移所有权，避免再次复制正文。
        this.body = body == null ? new byte[0] : body;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public int getStatus() {
        return statusCode.value();
    }

    public boolean isSuccessful() {
        return statusCode.is2xxSuccessful();
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public String getBodyAsString() {
        Charset charset = headers.getContentType() == null || headers.getContentType().getCharset() == null
                ? StandardCharsets.UTF_8
                : headers.getContentType().getCharset();
        return new String(body, charset);
    }

    public <T> T bodyAs(Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new HttpRequestException("DESERIALIZE", "response body", getStatus(), body, exception);
        }
    }

    public <T> T bodyAs(TypeReference<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new HttpRequestException("DESERIALIZE", "response body", getStatus(), body, exception);
        }
    }
}

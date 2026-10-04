package com.yulinlin.data.core.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Immutable HTTP response with convenient text and JSON accessors.
 */
public final class HttpResponse {

    private final HttpStatusCode statusCode;
    private final HttpHeaders headers;
    private final byte[] body;
    private final ObjectMapper objectMapper;

    HttpResponse(HttpStatusCode statusCode, HttpHeaders headers, byte[] body, ObjectMapper objectMapper) {
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode must not be null");
        HttpHeaders copiedHeaders = new HttpHeaders();
        copiedHeaders.putAll(Objects.requireNonNull(headers, "headers must not be null"));
        this.headers = HttpHeaders.readOnlyHttpHeaders(copiedHeaders);
        this.body = body == null ? new byte[0] : body.clone();
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

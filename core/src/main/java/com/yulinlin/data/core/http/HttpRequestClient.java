package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Objects;

/**
 * Thread-safe entry point for creating HTTP requests. Reuse one instance for connection reuse.
 */
public final class HttpRequestClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;



    public HttpRequestClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");

    }


    /**
     * Creates a reusable client with a default request-response timeout.
     */
    public static HttpRequestClient withTimeout(
            RestClient.Builder builder, ObjectMapper objectMapper, Duration timeout) {
        return new HttpRequestClient(HttpRequestFactory.withTimeout(builder, timeout), objectMapper);
    }

    public HttpRequest get(String url) {
        return request(HttpMethod.GET, url);
    }

    public HttpRequest post(String url) {
        return request(HttpMethod.POST, url);
    }

    public HttpRequest put(String url) {
        return request(HttpMethod.PUT, url);
    }

    public HttpRequest patch(String url) {
        return request(HttpMethod.PATCH, url);
    }

    public HttpRequest delete(String url) {
        return request(HttpMethod.DELETE, url);
    }

    public HttpRequest request(HttpMethod method, String url) {
        return new HttpRequest(restClient, objectMapper, method, url);
    }

    /**
     * Checks whether a resource is explicitly reported as missing by a GET request.
     * Network failures and statuses other than 404 return {@code false}.
     */
    public boolean isNotFound(String url) {
        try {
            get(url).execute();
            return false;
        } catch (HttpRequestException exception) {
            return Integer.valueOf(404).equals(exception.getStatusCode());
        }
    }


}

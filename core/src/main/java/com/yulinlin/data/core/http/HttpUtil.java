package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Convenient static HTTP entry point for simple synchronous requests.
 * <p>
 * Spring Boot applications should prefer injecting {@link HttpRequestClient}
 * when they need the application's configured {@link RestClient.Builder}.
 */
public final class HttpUtil {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static  HttpRequestClient DEFAULT_CLIENT =
            HttpRequestClient.withTimeout(RestClient.builder(), new ObjectMapper(), DEFAULT_TIMEOUT);

    private HttpUtil() {
    }

    public static void setClient(HttpRequestClient defaultClient) {
        DEFAULT_CLIENT = defaultClient;
    }

    public static HttpRequest get(String url) {
        return DEFAULT_CLIENT.get(url);
    }

    public static HttpRequest post(String url) {
        return DEFAULT_CLIENT.post(url);
    }

    public static HttpRequest put(String url) {
        return DEFAULT_CLIENT.put(url);
    }

    public static HttpRequest patch(String url) {
        return DEFAULT_CLIENT.patch(url);
    }

    public static HttpRequest delete(String url) {
        return DEFAULT_CLIENT.delete(url);
    }

    public static HttpRequest request(HttpMethod method, String url) {
        return DEFAULT_CLIENT.request(method, url);
    }

    /**
     * Creates a client with the supplied default request-response timeout.
     * Keep the returned client as a field when issuing more than one request, so its
     * underlying HTTP connection pool can be reused.
     */
    public static HttpRequestClient withTimeout(Duration timeout) {
        return HttpRequestClient.withTimeout(RestClient.builder(), new ObjectMapper(), timeout);
    }

    public static boolean isNotFound(String url) {
        return DEFAULT_CLIENT.isNotFound(url);
    }

    public static HttpResponse download(String url, Path destination) {
        return get(url).downloadTo(destination);
    }
}

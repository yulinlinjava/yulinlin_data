package com.yulinlin.data.core.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

final class HttpRequestFactory {

    private HttpRequestFactory() {
    }

    static RestClient withTimeout(RestClient.Builder builder, Duration timeout) {
        return Objects.requireNonNull(builder, "builder must not be null")
                .requestFactory(requestFactory(timeout))
                .build();
    }

    static RestClient withTimeout(RestClient client, Duration timeout) {
        return Objects.requireNonNull(client, "client must not be null")
                .mutate()
                .requestFactory(requestFactory(timeout))
                .build();
    }

    static Duration requirePositiveTimeout(Duration timeout) {
        Duration value = Objects.requireNonNull(timeout, "timeout must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("timeout must be greater than zero");
        }
        return value;
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        Duration value = requirePositiveTimeout(timeout);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(value)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(value);
        return requestFactory;
    }
}

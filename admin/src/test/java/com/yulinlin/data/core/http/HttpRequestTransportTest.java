package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 仅本机回环 HTTP，不依赖公网；覆盖实际连接复用与响应正文超时。 */
class HttpRequestTransportTest {
    private HttpServer server;
    private ExecutorService workers;
    private String base;
    private final List<Integer> ports = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/", exchange -> {
            ports.add(exchange.getRemoteAddress().getPort());
            try (exchange) {
                byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                if (exchange.getRequestURI().getPath().equals("/slow")) {
                    exchange.getResponseBody().write(body, 0, 1);
                    exchange.getResponseBody().flush();
                    try { Thread.sleep(300); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
                    exchange.getResponseBody().write(body, 1, body.length - 1);
                } else {
                    exchange.getResponseBody().write(body);
                }
            } catch (IOException ignored) {
                // 超时用例会主动关闭客户端连接。
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
        if (workers != null) workers.shutdownNow();
    }

    @Test
    void differentPerRequestTimeoutsReuseConnection() {
        RestClient.Builder builder = RestClient.builder().defaultHeader("X-Test", "retained");
        try (HttpRequestClient client = HttpRequestClient.withTimeout(builder, new ObjectMapper(), Duration.ofSeconds(2))) {
            for (int seconds : List.of(1, 2, 3)) {
                assertEquals("hello", client.get(base).timeout(Duration.ofSeconds(seconds)).execute().getBodyAsString());
            }
            assertEquals(1, ports.stream().distinct().count());
        }
    }

    @Test
    void concurrentTimeoutsAreIsolatedAndFailureDoesNotAffectNextRequest() throws Exception {
        try (HttpRequestClient client = HttpRequestClient.withTimeout(RestClient.builder(), new ObjectMapper(), Duration.ofSeconds(3));
             ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var shortRequest = callers.submit(() -> assertThrows(HttpRequestException.class,
                    () -> client.get(base + "/slow").timeout(Duration.ofMillis(50)).execute()));
            var longRequest = callers.submit(() -> client.get(base + "/slow")
                    .timeout(Duration.ofSeconds(3)).execute().getBodyAsString());
            assertNotNull(shortRequest.get(5, TimeUnit.SECONDS));
            assertEquals("hello", longRequest.get(5, TimeUnit.SECONDS));
            assertEquals("hello", client.get(base + "/slow").execute().getBodyAsString());
        }
    }

    @Test
    void scopedTimeoutIsRestoredAfterNestedCallAndFailure() {
        try (HttpRequestFactory factory = new HttpRequestFactory(Duration.ofSeconds(10), null)) {
            @SuppressWarnings("unchecked")
            ThreadLocal<Duration> scoped = (ThreadLocal<Duration>) ReflectionTestUtils.getField(factory, "requestTimeout");
            assertNotNull(scoped);
            factory.withTimeout(Duration.ofSeconds(1), () -> {
                assertEquals(Duration.ofSeconds(1), scoped.get());
                factory.withTimeout(null, () -> { assertNull(scoped.get()); return null; });
                assertEquals(Duration.ofSeconds(1), scoped.get());
                assertThrows(IllegalArgumentException.class, () -> factory.withTimeout(Duration.ofSeconds(2), () -> {
                    throw new IllegalArgumentException("failure");
                }));
                assertEquals(Duration.ofSeconds(1), scoped.get());
                return null;
            });
            assertNull(scoped.get());
            scoped.remove();
        }
    }

    @Test
    void suppliedTransportIsRetainedAndNotClosedByWrapper() {
        try (HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpRequestClient client = HttpRequestClient.create(RestClient.builder(), new ObjectMapper(),
                    new HttpRequestProperties(), transport);
            assertEquals("hello", client.get(base).timeout(Duration.ofSeconds(1)).execute().getBodyAsString());
            client.close();
            assertFalse(transport.isTerminated());
            assertEquals("hello", client.get(base).execute().getBodyAsString());
        }
    }

    @Test
    void rejectsZeroNegativeAndSubMillisecondTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequestFactory.requirePositiveTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> HttpRequestFactory.requirePositiveTimeout(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> HttpRequestFactory.requirePositiveTimeout(Duration.ofNanos(1)));
    }
}

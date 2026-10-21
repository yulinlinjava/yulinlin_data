package com.yulinlin.regression.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.yulinlin.data.core.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 放在外部包验证 SPI 可访问性，不使用反射访问请求、文件或响应内部实现。 */
class HttpExecutorTest {
    @Test
    void customExecutorReceivesResolvedSettingsAndEncodedUrl() {
        RecordingExecutor executor = new RecordingExecutor();
        HttpRequestProperties properties = new HttpRequestProperties();
        properties.setTimeout(Duration.ofSeconds(8));
        properties.setMaxResponseSize(DataSize.ofBytes(100));
        properties.setMaxErrorSize(DataSize.ofBytes(20));
        HttpRequestClient client = new HttpRequestClient(executor, new ObjectMapper(), properties);
        client.post("https://example.test/a%2Fb?x=1&y=2&x=3")
                .query("q", "a+b").header("X-Test", "yes").json(Map.of("enabled", false))
                .timeout(Duration.ofSeconds(2)).execute();
        HttpRequestData request = executor.requests.getFirst();
        assertEquals(HttpMethod.POST, request.getMethod());
        assertEquals("https://example.test/a%2Fb?x=1&y=2&x=3&q=a%2Bb", request.getUri().toString());
        assertEquals(Duration.ofSeconds(2), request.getTimeout());
        assertTrue(request.isTimeoutOverridden());
        assertEquals(100, request.getMaxResponseSize());
        assertEquals(20, request.getMaxErrorSize());
        assertEquals("yes", request.getHeaders().getFirst("X-Test"));
        assertEquals(HttpRequestData.BodyKind.JSON, request.getBodyKind());
        assertEquals(MediaType.APPLICATION_JSON, request.getContentType());
        assertEquals(Map.of("enabled", false), request.getBody());
        client.get("https://example.test").execute();
        assertEquals(Duration.ofSeconds(8), executor.requests.getLast().getTimeout());
        assertFalse(executor.requests.getLast().isTimeoutOverridden());
    }

    @Test
    void snapshotsContainersWithoutDeepCloningBusinessObjects() {
        HttpRequestClient client = new HttpRequestClient(new RecordingExecutor());
        HttpRequest builder = client.post("https://example.test").header("X-Test", "one").form("name", "alice");
        HttpRequestData snapshot = builder.toRequestData();
        builder.header("X-Test", "two").form("name", "bob");
        assertEquals(List.of("one"), snapshot.getHeaders().get("X-Test"));
        assertEquals(List.of("alice"), snapshot.getForm().get("name"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getHeaders().add("X-New", "x"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getHeaders().get("X-Test").add("x"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getForm().add("name", "x"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getForm().get("name").add("x"));
        Map<String, Object> body = Map.of("id", "1");
        assertSame(body, client.post("https://example.test").json(body).toRequestData().getBody());
    }

    @Test
    void externalExecutorCanReadMultipartAndConstructResponses() throws Exception {
        HttpRequestClient client = new HttpRequestClient(new RecordingExecutor());
        byte[] bytes = "contents".getBytes(StandardCharsets.UTF_8);
        HttpFile file = HttpFile.of("file.txt", new ByteArrayInputStream(bytes), MediaType.TEXT_PLAIN);
        HttpRequestData data = client.post("https://example.test").multipart("tag", "demo").file("file", file).toRequestData();
        assertSame(file, data.getMultipart().getFirst("file"));
        assertEquals("demo", data.getMultipart().getFirst("tag"));
        assertEquals("file.txt", file.getFilename());
        assertEquals(-1, file.getContentLength());
        assertFalse(file.isRepeatable());
        assertEquals(MediaType.TEXT_PLAIN, file.getContentType());
        assertNotNull(file.getResource());
        try (var input = file.openStream()) { assertArrayEquals(bytes, input.readAllBytes()); }

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Test", "old");
        HttpResponse response = HttpResponse.of(200, headers, bytes);
        bytes[0] = 'X';
        headers.set("X-Test", "new");
        assertEquals("contents", response.getBodyAsString());
        assertEquals("old", response.getHeaders().getFirst("X-Test"));
        assertDoesNotThrow(() -> new HttpRequestException("GET", "https://example.test", null, null,
                new IllegalStateException("network failure")));
    }

    @Test
    void headFallbackRemainsInFacadeAndNeverCallsExecute() {
        RecordingExecutor executor = new RecordingExecutor();
        HttpRequestClient client = new HttpRequestClient(executor);
        assertTrue(client.isNotFound("https://example.test"));
        assertEquals(List.of(HttpMethod.HEAD, HttpMethod.GET),
                executor.requests.stream().map(HttpRequestData::getMethod).toList());
        assertEquals(0, executor.executeCount);
    }

    @Test
    void clientMapperAlsoAppliesToCustomExecutorResponses() {
        RecordingExecutor executor = new RecordingExecutor();
        executor.response = HttpResponse.of(200, new HttpHeaders(),
                "{\"user_name\":\"alice\"}".getBytes(StandardCharsets.UTF_8));
        ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        User user = new HttpRequestClient(executor, mapper).get("https://example.test").execute().bodyAs(User.class);
        assertEquals("alice", user.userName());
    }

    @Test
    void timeoutViewReusesExecutorAndDoesNotCloseIt() {
        RecordingExecutor executor = new RecordingExecutor();
        HttpRequestClient original = new HttpRequestClient(executor);
        try (HttpRequestClient view = original.withTimeout(Duration.ofSeconds(3))) {
            assertSame(executor, view.getExecutor());
            view.get("https://example.test").execute();
            assertEquals(Duration.ofSeconds(3), executor.requests.getLast().getTimeout());
            assertTrue(executor.requests.getLast().isTimeoutOverridden());
        }
        original.get("https://example.test").execute();
        assertEquals(Duration.ofSeconds(10), executor.requests.getLast().getTimeout());
        original.close();
        assertEquals(0, executor.closeCount);
    }

    @Test
    void facadeDefensivelyNormalizesCustomErrorResponse() {
        RecordingExecutor executor = new RecordingExecutor();
        executor.response = HttpResponse.of(500, new HttpHeaders(), "123456".getBytes(StandardCharsets.UTF_8));
        HttpRequestProperties properties = new HttpRequestProperties();
        properties.setMaxErrorSize(DataSize.ofBytes(3));
        HttpRequestClient client = new HttpRequestClient(executor, new ObjectMapper(), properties);
        HttpRequestException error = assertThrows(HttpRequestException.class, () -> client.get("https://example.test").execute());
        assertEquals(500, error.getStatusCode().intValue());
        assertEquals("123", new String(error.getResponseBody(), StandardCharsets.UTF_8));
        assertTrue(error.isResponseBodyTruncated());
    }

    @Test
    void downloadDelegatesNormalizedTarget() {
        RecordingExecutor executor = new RecordingExecutor();
        Path target = Path.of("download", "report.txt");
        new HttpRequestClient(executor).get("https://example.test").downloadTo(target);
        assertEquals(target.toAbsolutePath().normalize(), executor.destination);
        assertEquals(0, executor.executeCount);
    }

    record User(String userName) { }

    static final class RecordingExecutor implements HttpExecutor {
        final List<HttpRequestData> requests = new ArrayList<>();
        HttpResponse response = HttpResponse.of(200, new HttpHeaders(), new byte[0]);
        int executeCount;
        int closeCount;
        Path destination;
        @Override public HttpResponse execute(HttpRequestData request) {
            requests.add(request);
            executeCount++;
            return response;
        }
        @Override public int status(HttpRequestData request) {
            requests.add(request);
            return HttpMethod.HEAD.equals(request.getMethod()) ? 405 : 404;
        }
        @Override public HttpResponse download(HttpRequestData request, Path destination) {
            requests.add(request);
            this.destination = destination;
            return response;
        }
        @Override public void close() { closeCount++; }
    }
}

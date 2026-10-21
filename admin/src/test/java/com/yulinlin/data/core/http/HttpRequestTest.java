package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP 回归测试放在 admin，不启动 Spring、不做配置绑定，也不连接外部服务。 */
class HttpRequestTest {
    @TempDir Path directory;

    @Test
    void preservesSignedUrlOrderEscapesAndStrictlyEncodesNewQuery() {
        RecordingFactory factory = new RecordingFactory(method -> response("ok", HttpStatus.OK));
        client(factory).get("https://example.test/a%2Fb?x=1&y=2&x=3&flag&sig=%2b%252F#section")
                .query("token", "a+b &=%2F中文").query("skip", null).execute();
        assertEquals("https://example.test/a%2Fb?x=1&y=2&x=3&flag&sig=%2b%252F"
                        + "&token=a%2Bb%20%26%3D%252F%E4%B8%AD%E6%96%87#section",
                factory.requests.getFirst().getURI().toString());
    }

    @Test
    void encodesRawUnicodeSpaceAndRetainsBaseUrl() {
        RecordingFactory factory = new RecordingFactory(method -> response("ok", HttpStatus.OK));
        RestClient rest = RestClient.builder().baseUrl("https://example.test")
                .requestFactory(factory).build();
        new HttpRequestClient(rest, new ObjectMapper()).get("/中文 空格?q=1%2F2").execute();
        assertEquals("https://example.test/%E4%B8%AD%E6%96%87%20%E7%A9%BA%E6%A0%BC?q=1%2F2",
                factory.requests.getFirst().getURI().toString());
    }

    @Test
    void questionMarkInExistingQueryIsNotMistakenForEmptyQuery() {
        RecordingFactory factory = new RecordingFactory(method -> response("ok", HttpStatus.OK));
        client(factory).get("https://example.test?q=why?").query("page", 1).execute();
        assertEquals("https://example.test?q=why?&page=1", factory.requests.getFirst().getURI().toString());
        client(factory).get("https://example.test?").query("page", 1).execute();
        assertEquals("https://example.test?page=1", factory.requests.getLast().getURI().toString());
    }

    @Test
    void keepsIpv6BracketsButEncodesBracketsInPath() {
        RecordingFactory factory = new RecordingFactory(method -> response("ok", HttpStatus.OK));
        client(factory).get("http://[::1]/item[1]").execute();
        assertEquals("http://[::1]/item%5B1%5D", factory.requests.getFirst().getURI().toString());
    }

    @Test
    void uploadsInputStreamWithoutConsumingItToCalculateLength() {
        RecordingFactory factory = new RecordingFactory(method -> response("ok", HttpStatus.OK));
        TrackingInputStream input = new TrackingInputStream("file-content");
        client(factory).post("https://example.test/upload")
                .multipart("category", "report")
                .file("file", HttpFile.of("report.txt", input, MediaType.TEXT_PLAIN)).execute();
        String sent = factory.requests.getFirst().getBodyAsString(StandardCharsets.UTF_8);
        assertTrue(sent.contains("filename=\"report.txt\""));
        assertTrue(sent.contains("file-content"));
        assertTrue(sent.contains("report"));
        assertTrue(input.closed);
    }

    @Test
    void jsonFormsAndAuthenticationStillWork() {
        RecordingFactory factory = new RecordingFactory(method -> response("{\"ok\":true}", HttpStatus.OK));
        HttpRequestClient client = client(factory);
        client.post("https://example.test").bearerToken("demo").json(Map.of("enabled", false)).execute();
        MockClientHttpRequest json = factory.requests.getFirst();
        assertEquals("Bearer demo", json.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("{\"enabled\":false}", json.getBodyAsString(StandardCharsets.UTF_8));
        client.post("https://example.test").form("token", "a+b").execute();
        assertEquals("token=a%2Bb", factory.requests.getLast().getBodyAsString(StandardCharsets.UTF_8));
    }

    @Test
    void statusDoesNotReadBodyEvenIfResponseCloseTriesToDrain() {
        TrackingInputStream input = new TrackingInputStream("large-file");
        RecordingFactory factory = new RecordingFactory(method -> drainingResponse(input, HttpStatus.NOT_FOUND));
        assertEquals(404, client(factory).get("https://example.test/file").status());
        assertEquals(0, input.readCount);
        assertTrue(input.closed);
    }

    @Test
    void missingCheckFallsBackOnlyWhenHeadUnsupported() {
        TrackingInputStream head = new TrackingInputStream("head");
        TrackingInputStream get = new TrackingInputStream("get");
        RecordingFactory factory = new RecordingFactory(method -> HttpMethod.HEAD.equals(method)
                ? drainingResponse(head, HttpStatus.METHOD_NOT_ALLOWED)
                : drainingResponse(get, HttpStatus.NOT_FOUND));
        assertTrue(client(factory).isNotFound("https://example.test/file"));
        assertEquals(List.of(HttpMethod.HEAD, HttpMethod.GET),
                factory.requests.stream().map(MockClientHttpRequest::getMethod).toList());
        assertEquals(0, head.readCount + get.readCount);
        RecordingFactory forbidden = new RecordingFactory(method -> response("", HttpStatus.FORBIDDEN));
        assertFalse(client(forbidden).isNotFound("https://example.test"));
        assertEquals(1, forbidden.requests.size());
    }

    @Test
    void missingCheckDoesNotTurnNetworkFailureIntoMissing() {
        HttpRequestClient client = client((uri, method) -> { throw new IOException("offline"); });
        assertFalse(client.isNotFound("https://example.test"));
        assertThrows(HttpRequestException.class, () -> client.get("https://example.test").status());
    }

    @Test
    void capsChunkedResponseAndDoesNotDrainRemainder() {
        TrackingInputStream input = new TrackingInputStream("1234567890");
        RecordingFactory factory = new RecordingFactory(method -> drainingResponse(input, HttpStatus.OK));
        HttpRequestException error = assertThrows(HttpRequestException.class,
                () -> limitedClient(factory).get("https://example.test").execute());
        assertTrue(error.getCause().getMessage().contains("max-response-size"));
        assertEquals(5, input.readCount); // 上限 4，再读取 1 字节检查越界。
        assertTrue(input.closed);
    }

    @Test
    void rejectsDeclaredOversizeWithoutReadingBody() {
        TrackingInputStream input = new TrackingInputStream("1234567890");
        MockClientHttpResponse response = drainingResponse(input, HttpStatus.OK);
        response.getHeaders().setContentLength(10);
        assertThrows(HttpRequestException.class, () -> limitedClient(new RecordingFactory(method -> response))
                .get("https://example.test").execute());
        assertEquals(0, input.readCount);
    }

    @Test
    void truncatesErrorsButPreservesStatusAndClosesStream() {
        TrackingInputStream input = new TrackingInputStream("1234567890");
        HttpRequestException error = assertThrows(HttpRequestException.class,
                () -> limitedClient(new RecordingFactory(method -> drainingResponse(input, HttpStatus.BAD_REQUEST)))
                        .get("https://example.test").execute());
        assertEquals(400, error.getStatusCode().intValue());
        assertEquals("123", new String(error.getResponseBody(), StandardCharsets.UTF_8));
        assertTrue(error.isResponseBodyTruncated());
        assertEquals(4, input.readCount);
        assertTrue(input.closed);
    }

    @Test
    void headContentLengthDoesNotCountAsResponseBody() {
        MockClientHttpResponse response = response("", HttpStatus.OK);
        response.getHeaders().setContentLength(1_000_000);
        assertEquals(0, limitedClient(new RecordingFactory(method -> response))
                .head("https://example.test").execute().getBody().length);
    }

    @Test
    void downloadReplacesOnlyAfterCompleteAndIgnoresInMemoryLimit() throws IOException {
        Path target = directory.resolve("download.txt");
        Files.writeString(target, "old");
        MockClientHttpResponse response = response("new-content", HttpStatus.OK);
        response.getHeaders().setContentLength(11);
        HttpResponse result = limitedClient(new RecordingFactory(method -> response))
                .get("https://example.test").downloadTo(target);
        assertEquals("new-content", Files.readString(target));
        assertEquals(0, result.getBody().length);
        assertOnlyTargetRemains();
    }

    @Test
    void failedOrShortDownloadKeepsOldFileAndRemovesTemporary() throws IOException {
        Path target = directory.resolve("download.txt");
        Files.writeString(target, "old");
        MockClientHttpResponse shortBody = response("short", HttpStatus.OK);
        shortBody.getHeaders().setContentLength(99);
        assertThrows(HttpRequestException.class, () -> client(new RecordingFactory(method -> shortBody))
                .get("https://example.test").downloadTo(target));
        assertEquals("old", Files.readString(target));
        assertOnlyTargetRemains();

        InputStream broken = new InputStream() {
            int remaining = 3;
            @Override public int read() throws IOException {
                if (remaining-- > 0) return 'x';
                throw new IOException("connection lost");
            }
        };
        assertThrows(HttpRequestException.class, () -> client(new RecordingFactory(method ->
                new MockClientHttpResponse(broken, HttpStatus.OK))).get("https://example.test").downloadTo(target));
        assertEquals("old", Files.readString(target));
        assertOnlyTargetRemains();
    }

    @Test
    void redirectsPartialAndNoContentNeverOverwriteTarget() throws IOException {
        Path target = directory.resolve("download.txt");
        Files.writeString(target, "old");
        for (HttpStatus status : List.of(HttpStatus.FOUND, HttpStatus.PARTIAL_CONTENT, HttpStatus.NO_CONTENT)) {
            assertThrows(HttpRequestException.class, () -> client(new RecordingFactory(method -> response("bad", status)))
                    .get("https://example.test").downloadTo(target));
            assertEquals("old", Files.readString(target));
        }
        assertOnlyTargetRemains();
    }

    @Test
    void customRestClientIsPreservedAndUnsafeTimeoutReplacementIsRejected() {
        RecordingFactory factory = new RecordingFactory(method -> response("ok", HttpStatus.OK));
        RestClient rest = RestClient.builder().requestFactory(factory).defaultHeader("X-Custom", "retained").build();
        HttpRequestClient client = new HttpRequestClient(rest, new ObjectMapper());
        client.get("https://example.test").execute();
        assertEquals("retained", factory.requests.getFirst().getHeaders().getFirst("X-Custom"));
        assertThrows(IllegalStateException.class, () -> client.get("https://example.test")
                .timeout(Duration.ofSeconds(2)).execute());
        assertEquals(1, factory.requests.size());
    }

    @Test
    void responseHeadersAndBodyCannotBeMutatedByTheCaller() {
        MockClientHttpResponse response = response("ok", HttpStatus.OK);
        response.getHeaders().add("X-Test", "before");
        HttpResponse result = client(new RecordingFactory(method -> response)).get("https://example.test").execute();
        response.getHeaders().get("X-Test").set(0, "after");
        assertEquals("before", result.getHeaders().getFirst("X-Test"));
        byte[] bytes = result.getBody();
        bytes[0] = 'x';
        assertEquals("ok", result.getBodyAsString());
    }

    private void assertOnlyTargetRemains() throws IOException {
        try (var files = Files.list(directory)) {
            assertEquals(List.of("download.txt"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

    private static HttpRequestClient client(ClientHttpRequestFactory factory) {
        return new HttpRequestClient(RestClient.builder().requestFactory(factory).build(), new ObjectMapper());
    }

    private static HttpRequestClient limitedClient(ClientHttpRequestFactory factory) {
        HttpRequestProperties properties = new HttpRequestProperties();
        properties.setMaxResponseSize(DataSize.ofBytes(4));
        properties.setMaxErrorSize(DataSize.ofBytes(3));
        return new HttpRequestClient(RestClient.builder().requestFactory(factory).build(), new ObjectMapper(), properties);
    }

    private static MockClientHttpResponse response(String body, HttpStatus status) {
        return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
    }

    private static MockClientHttpResponse drainingResponse(TrackingInputStream input, HttpStatus status) {
        return new MockClientHttpResponse(input, status) {
            @Override public void close() {
                try { input.transferTo(java.io.OutputStream.nullOutputStream()); }
                catch (IOException ignored) { }
                finally { input.close(); }
            }
        };
    }

    private static final class RecordingFactory implements ClientHttpRequestFactory {
        final List<MockClientHttpRequest> requests = new ArrayList<>();
        final Function<HttpMethod, ClientHttpResponse> responses;
        RecordingFactory(Function<HttpMethod, ClientHttpResponse> responses) { this.responses = responses; }
        @Override public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            request.setResponse(responses.apply(method));
            requests.add(request);
            return request;
        }
    }

    private static final class TrackingInputStream extends InputStream {
        final ByteArrayInputStream input;
        int readCount;
        boolean closed;
        TrackingInputStream(String text) { input = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
        @Override public int read() throws IOException {
            if (closed) throw new IOException("closed");
            int value = input.read();
            if (value != -1) readCount++;
            return value;
        }
        @Override public void close() { closed = true; }
    }
}

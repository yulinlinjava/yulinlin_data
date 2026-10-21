package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/** 默认执行器：Spring RestClient + 共享 JDK HttpClient，保留流式上传下载和请求级响应超时。 */
public final class RestClientHttpExecutor implements HttpExecutor {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HttpRequestFactory requestFactory;

    /** 完整保留外部 RestClient 的网络设置；显式单次超时不能安全覆盖时拒绝执行。 */
    public RestClientHttpExecutor(RestClient restClient, ObjectMapper objectMapper) {
        this(restClient, objectMapper, null);
    }

    private RestClientHttpExecutor(RestClient restClient, ObjectMapper objectMapper, HttpRequestFactory factory) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.requestFactory = factory;
    }

    public static RestClientHttpExecutor create(RestClient.Builder builder, ObjectMapper mapper,
                                                HttpRequestProperties properties) {
        return create(builder, mapper, properties, null);
    }

    /** transport 不为空时，代理/证书/连接超时及其生命周期由调用方管理。 */
    public static RestClientHttpExecutor create(RestClient.Builder builder, ObjectMapper mapper,
                                                HttpRequestProperties properties, HttpClient transport) {
        Objects.requireNonNull(builder, "builder must not be null");
        Objects.requireNonNull(mapper, "objectMapper must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        HttpRequestFactory factory = new HttpRequestFactory(properties.getTimeout(), transport);
        try {
            return new RestClientHttpExecutor(builder.clone().requestFactory(factory).build(), mapper, factory);
        } catch (RuntimeException | Error error) {
            factory.close();
            throw error;
        }
    }

    @Override
    public HttpResponse execute(HttpRequestData data) {
        return invoke(data, () -> requestSpec(data).exchange((request, response) -> {
            HttpStatusCode status = response.getStatusCode();
            if (status.isError()) throw error(data, response);
            try (InputStream input = response.getBody()) {
                if (hasBody(data, status) && response.getHeaders().getContentLength() > data.getMaxResponseSize()) {
                    throw responseTooLarge(data, status);
                }
                byte[] bytes = hasBody(data, status) ? input.readNBytes(data.getMaxResponseSize() + 1) : new byte[0];
                if (bytes.length > data.getMaxResponseSize()) throw responseTooLarge(data, status);
                return new HttpResponse(status, response.getHeaders(), bytes, objectMapper);
            }
        }));
    }

    @Override
    public int status(HttpRequestData data) {
        return invoke(data, () -> requestSpec(data).exchange((request, response) -> {
            int status = response.getStatusCode().value();
            // 先关正文，阻止部分 Spring 适配器在 response.close() 中自动 drain 大文件。
            response.getBody().close();
            return status;
        }));
    }

    @Override
    public HttpResponse download(HttpRequestData data, Path destination) {
        Path target = Objects.requireNonNull(destination, "destination must not be null").toAbsolutePath().normalize();
        return invoke(data, () -> requestSpec(data).exchange((request, response) -> download(data, response, target)));
    }

    private <T> T invoke(HttpRequestData data, Supplier<T> operation) {
        Objects.requireNonNull(data, "request must not be null");
        try {
            if (requestFactory != null) return requestFactory.withTimeout(data.getTimeout(), operation);
            if (data.isTimeoutOverridden()) {
                throw new IllegalStateException("外部 RestClient 不支持安全覆盖单次超时，请在原请求工厂设置超时，"
                        + "或使用 RestClientHttpExecutor.create(..., HttpClient)；框架不会替换代理或证书配置");
            }
            return operation.get();
        } catch (RestClientResponseException exception) {
            byte[] body = exception.getResponseBodyAsByteArray();
            boolean truncated = body.length > data.getMaxErrorSize();
            throw new HttpRequestException(data.getMethod().name(), data.getUri().toString(),
                    exception.getStatusCode().value(),
                    truncated ? Arrays.copyOf(body, data.getMaxErrorSize()) : body, truncated, exception);
        } catch (RestClientException exception) {
            throw new HttpRequestException(data.getMethod().name(), data.getUri().toString(), null, null, exception);
        }
    }

    private HttpResponse download(HttpRequestData data, ClientHttpResponse response, Path target) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (!status.is2xxSuccessful()) throw error(data, response);
        if (!hasBody(data, status) || status.value() == 206 || response.getHeaders().containsKey(HttpHeaders.CONTENT_RANGE)) {
            response.getBody().close();
            throw new HttpRequestException(data.getMethod().name(), data.getUri().toString(), status.value(), null,
                    new IOException("下载需要完整文件响应，不接受 HEAD、无正文状态或分段响应"));
        }
        Path temporary = null;
        try (InputStream input = response.getBody()) {
            Path parent = target.getParent();
            if (parent == null || target.getFileName() == null) throw new IOException("下载目标必须是文件路径");
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".http-download-", ".part");
            long copied = Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            long expected = response.getHeaders().getContentLength();
            if (expected >= 0 && expected != copied) {
                throw new IOException("下载内容不完整，预期 " + expected + " 字节，实际 " + copied);
            }
        } catch (IOException | RuntimeException | Error failure) {
            deleteTemporary(temporary, failure);
            throw failure;
        }
        try {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | Error failure) {
            deleteTemporary(temporary, failure);
            throw failure;
        }
        return new HttpResponse(status, response.getHeaders(), new byte[0], objectMapper);
    }

    private static void deleteTemporary(Path temporary, Throwable failure) {
        if (temporary == null) return;
        try { Files.deleteIfExists(temporary); }
        catch (IOException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
    }

    private static boolean hasBody(HttpRequestData data, HttpStatusCode status) {
        return !HttpMethod.HEAD.equals(data.getMethod()) && !status.is1xxInformational()
                && status.value() != 204 && status.value() != 205 && status.value() != 304;
    }

    private HttpRequestException error(HttpRequestData data, ClientHttpResponse response) throws IOException {
        try (InputStream input = response.getBody()) {
            byte[] bytes = hasBody(data, response.getStatusCode())
                    ? input.readNBytes(data.getMaxErrorSize() + 1) : new byte[0];
            boolean truncated = bytes.length > data.getMaxErrorSize();
            return new HttpRequestException(data.getMethod().name(), data.getUri().toString(),
                    response.getStatusCode().value(),
                    truncated ? Arrays.copyOf(bytes, data.getMaxErrorSize()) : bytes, truncated, null);
        }
    }

    private HttpRequestException responseTooLarge(HttpRequestData data, HttpStatusCode status) {
        return new HttpRequestException(data.getMethod().name(), data.getUri().toString(), status.value(), null,
                new IOException("响应正文超过 max-response-size 限制：" + data.getMaxResponseSize() + " 字节；大文件请使用 downloadTo"));
    }

    private RestClient.RequestHeadersSpec<?> requestSpec(HttpRequestData data) {
        RestClient.RequestBodySpec spec = restClient.method(data.getMethod()).uri(data.getUri())
                .headers(headers -> headers.addAll(data.getHeaders()));
        return switch (data.getBodyKind()) {
            case NONE -> spec;
            case JSON, RAW -> spec.contentType(data.getContentType()).body(data.getBody());
            case FORM -> spec.contentType(data.getContentType()).body(data.getForm());
            case MULTIPART -> spec.contentType(data.getContentType()).body(multipart(data));
        };
    }

    private MultiValueMap<String, Object> multipart(HttpRequestData data) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        data.getMultipart().forEach((name, values) -> values.forEach(value -> {
            if (value instanceof HttpFile file) {
                if (file.getContentType() == null) parts.add(name, file.getResource());
                else {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(file.getContentType());
                    parts.add(name, new HttpEntity<Resource>(file.getResource(), headers));
                }
            } else parts.add(name, value);
        }));
        return parts;
    }

    @Override
    public void close() {
        if (requestFactory != null) requestFactory.close();
    }
}

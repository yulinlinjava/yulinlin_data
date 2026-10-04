package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable request builder supporting JSON, URL encoded forms, multipart uploads and downloads.
 */
public final class HttpRequest {

    private enum BodyKind { NONE, JSON, FORM, MULTIPART, RAW }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
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

    HttpRequest(RestClient restClient, ObjectMapper objectMapper, HttpMethod method, String url) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.url = Objects.requireNonNull(url, "url must not be null");
    }

    public HttpRequest query(String name, Object value) {
        if (value != null) {
            query.add(name, String.valueOf(value));
        }
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

    /**
     * Sends a body with an explicit content type, for example text, XML or byte arrays.
     */
    public HttpRequest body(Object value, MediaType contentType) {
        switchBodyKind(BodyKind.RAW);
        body = Objects.requireNonNull(value, "body must not be null");
        rawContentType = Objects.requireNonNull(contentType, "contentType must not be null");
        return this;
    }

    public HttpRequest form(String name, Object value) {
        switchBodyKind(BodyKind.FORM);
        form.add(name, value == null ? "" : String.valueOf(value));
        return this;
    }

    public HttpRequest form(Map<String,Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            form(entry.getKey(),entry.getValue());
        }
        return this;
    }

    public HttpRequest multipart(String name, Object value) {
        switchBodyKind(BodyKind.MULTIPART);
        multipart.add(name, value == null ? "" : String.valueOf(value));
        return this;
    }

    public HttpRequest file(String name, HttpFile file) {
        switchBodyKind(BodyKind.MULTIPART);
        HttpFile value = Objects.requireNonNull(file, "file must not be null");
        if (value.contentType() == null) {
            multipart.add(name, value.resource());
            return this;
        }
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(value.contentType());
        multipart.add(name, new HttpEntity<Resource>(value.resource(), partHeaders));
        return this;
    }

    /**
     * Sets the maximum time for this request to receive and read its response.
     */
    public HttpRequest timeout(Duration timeout) {
        this.timeout = HttpRequestFactory.requirePositiveTimeout(timeout);
        return this;
    }

    public HttpResponse execute() {
        URI requestUri = requestUri();
        try {
            org.springframework.http.ResponseEntity<byte[]> response = requestSpec(requestUri)
                    .retrieve()
                    .toEntity(byte[].class);
            return new HttpResponse(response.getStatusCode(), response.getHeaders(), response.getBody(), objectMapper);
        } catch (RestClientResponseException exception) {
            throw new HttpRequestException(method.name(), requestUri.toString(), exception.getStatusCode().value(),
                    exception.getResponseBodyAsByteArray(), exception);
        } catch (RestClientException exception) {
            throw new HttpRequestException(method.name(), requestUri.toString(), null, null, exception);
        }
    }

    public HttpResponse downloadTo(Path destination) {
        URI requestUri = requestUri();
        Path target = Objects.requireNonNull(destination, "destination must not be null").toAbsolutePath().normalize();
        try {
            return requestSpec(requestUri).exchange((request, response) -> download(response, target, requestUri));
        } catch (RestClientResponseException exception) {
            throw new HttpRequestException(method.name(), requestUri.toString(), exception.getStatusCode().value(),
                    exception.getResponseBodyAsByteArray(), exception);
        } catch (RestClientException exception) {
            throw new HttpRequestException(method.name(), requestUri.toString(), null, null, exception);
        }
    }

    private HttpResponse download(ClientHttpResponse response, Path target, URI requestUri) throws IOException {
        HttpStatusCode statusCode = response.getStatusCode();
        if (statusCode.isError()) {
            byte[] errorBody = response.getBody().readAllBytes();
            throw new HttpRequestException(method.name(), requestUri.toString(), statusCode.value(), errorBody, null);
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream input = response.getBody()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new HttpResponse(statusCode, response.getHeaders(), new byte[0], objectMapper);
    }

    private RestClient.RequestHeadersSpec<?> requestSpec(URI requestUri) {
        RestClient.RequestBodySpec spec = activeRestClient().method(method)
                .uri(requestUri)
                .headers(requestHeaders -> requestHeaders.addAll(headers));
        return switch (bodyKind) {
            case NONE -> spec;
            case JSON -> spec.contentType(MediaType.APPLICATION_JSON).body(body);
            case FORM -> spec.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form);
            case MULTIPART -> spec.contentType(MediaType.MULTIPART_FORM_DATA).body(multipart);
            case RAW -> spec.contentType(rawContentType).body(body);
        };
    }

    private RestClient activeRestClient() {
        if (timeout == null) {
            return restClient;
        }
        return HttpRequestFactory.withTimeout(restClient, timeout);
    }

    private URI requestUri() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        query.forEach((name, values) -> values.forEach(value -> builder.queryParam(name, value)));
        return builder.build().encode().toUri();
    }

    private void switchBodyKind(BodyKind requestedBodyKind) {
        if (bodyKind != BodyKind.NONE && bodyKind != requestedBodyKind) {
            throw new IllegalStateException("A request can have only one body type");
        }
        bodyKind = requestedBodyKind;
    }



}

package com.yulinlin.data.core.http;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A file part for a multipart/form-data request.
 */
public final class HttpFile {

    private final Resource resource;
    private final MediaType contentType;

    private HttpFile(Resource resource, MediaType contentType) {
        this.resource = Objects.requireNonNull(resource, "resource must not be null");
        this.contentType = contentType;
    }

    public static HttpFile of(Path path) {
        return of(path, null);
    }

    public static HttpFile of(Path path, MediaType contentType) {
        return new HttpFile(new FileSystemResource(Objects.requireNonNull(path, "path must not be null")), contentType);
    }

    public static HttpFile of(String filename, byte[] bytes) {
        return of(filename, bytes, null);
    }

    public static HttpFile of(String filename, byte[] bytes, MediaType contentType) {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(bytes, "bytes must not be null");
        Resource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new HttpFile(resource, contentType);
    }

    /**
     * The supplied stream is consumed once when the request is sent and cannot be retried safely.
     */
    public static HttpFile of(String filename, InputStream inputStream, MediaType contentType) {
        Objects.requireNonNull(filename, "filename must not be null");
        Resource resource = new InputStreamResource(Objects.requireNonNull(inputStream, "inputStream must not be null")) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new HttpFile(resource, contentType);
    }

    Resource resource() {
        return resource;
    }

    MediaType contentType() {
        return contentType;
    }
}

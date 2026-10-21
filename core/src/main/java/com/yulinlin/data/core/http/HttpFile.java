package com.yulinlin.data.core.http;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.io.IOException;
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
            public long contentLength() {
                // 不能通过读取输入流计算长度，否则上传时流已经被消费。
                return -1;
            }

            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new HttpFile(resource, contentType);
    }

    /** 公开只读资源入口，方便自定义执行器适配 Spring 或其他 HTTP 客户端。 */
    public Resource getResource() {
        return resource;
    }

    public MediaType getContentType() {
        return contentType;
    }

    public String getFilename() { return resource.getFilename(); }

    /** 未知长度返回 -1，不消费输入流；已知路径/字节数组返回实际长度。 */
    public long getContentLength() throws IOException { return resource.contentLength(); }

    /** 输入流类型只允许打开一次。执行器负责发送后的关闭，构造失败时调用方也应关闭原流。 */
    public InputStream openStream() throws IOException { return resource.getInputStream(); }

    public boolean isRepeatable() { return !(resource instanceof InputStreamResource); }
}

package com.yulinlin.data.core.http;

/**
 * Raised when an HTTP request cannot be completed or returns an error status.
 */
public class HttpRequestException extends RuntimeException {

    private final String method;
    private final String url;
    private final Integer statusCode;
    private final byte[] responseBody;

    HttpRequestException(String method, String url, Integer statusCode, byte[] responseBody, Throwable cause) {
        super(buildMessage(method, url, statusCode), cause);
        this.method = method;
        this.url = url;
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? new byte[0] : responseBody.clone();
    }

    private static String buildMessage(String method, String url, Integer statusCode) {
        String status = statusCode == null ? "request failed" : "HTTP " + statusCode;
        return method + " " + url + " failed: " + status;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public byte[] getResponseBody() {
        return responseBody.clone();
    }
}

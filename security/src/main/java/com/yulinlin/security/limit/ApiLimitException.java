package com.yulinlin.security.limit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/** Rate-limit rejection; applications can handle this exception to choose their response body. */
public final class ApiLimitException extends ResponseStatusException {
    private final int count;
    private final Duration interval;
    private final LimitBy by;
    private final long retryAfterSeconds;
    private final HttpHeaders headers;

    ApiLimitException(int count, Duration interval, LimitBy by, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        this.count = count;
        this.interval = interval;
        this.by = by;
        this.retryAfterSeconds = retryAfterSeconds;
        var values = new HttpHeaders();
        values.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        this.headers = HttpHeaders.readOnlyHttpHeaders(values);
    }

    public int getCount() { return count; }
    public Duration getInterval() { return interval; }
    public LimitBy getBy() { return by; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    @Override public HttpHeaders getHeaders() { return headers; }
}

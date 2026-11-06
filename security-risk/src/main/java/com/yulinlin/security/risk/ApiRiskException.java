package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.BehaviorEvent;
import com.yulinlin.security.risk.RiskModels.Decision;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** Behavioral-risk rejection; the application decides which message and details to return. */
public final class ApiRiskException extends ResponseStatusException {
    private final BehaviorEvent event;
    private final Decision decision;
    private final long retryAfterSeconds;
    private final HttpHeaders headers;

    public ApiRiskException(BehaviorEvent event, Decision decision) {
        this(event, decision, System.currentTimeMillis());
    }

    ApiRiskException(BehaviorEvent event, Decision decision, long now) {
        super(HttpStatus.TOO_MANY_REQUESTS, "访问暂时受限，请稍后再试");
        this.event = Objects.requireNonNull(event, "event");
        this.decision = Objects.requireNonNull(decision, "decision");
        if (decision.allowed()) throw new IllegalArgumentException("放行结果不能构造 ApiRiskException");
        long remainingMillis = Math.max(0, decision.blockedUntil() - now);
        this.retryAfterSeconds = Math.max(1, Math.ceilDiv(remainingMillis, 1_000));
        var values = new HttpHeaders();
        values.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        this.headers = HttpHeaders.readOnlyHttpHeaders(values);
    }

    public BehaviorEvent getEvent() { return event; }
    public Decision getDecision() { return decision; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    @Override public HttpHeaders getHeaders() { return headers; }
}

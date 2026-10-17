package com.yulinlin.security.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.yulinlin.security.SecurityLimitProperties;
import com.yulinlin.security.annotation.ApiLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** High-throughput local rate limiter for controller methods annotated with {@link ApiLimit}. */
public final class ApiLimitInterceptor implements HandlerInterceptor {

    private static final LimitRule UNLIMITED = new LimitRule(0, 1, LimitBy.IP);
    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

    private final ClientIdentityResolver identityResolver;
    private final IpBlacklist ipBlacklist;
    private final Cache<LimitKey, LimitCounter> counters;
    private final ConcurrentMap<HandlerKey, LimitRule> rules = new ConcurrentHashMap<>();

    public ApiLimitInterceptor(SecurityLimitProperties properties) {
        this(properties, ip -> false);
    }

    public ApiLimitInterceptor(SecurityLimitProperties properties, IpBlacklist ipBlacklist) {
        this.identityResolver = new ClientIdentityResolver(properties);
        this.ipBlacklist = Objects.requireNonNull(ipBlacklist, "ipBlacklist must not be null");
        this.counters = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumSize())
                .scheduler(Scheduler.systemScheduler())
                .expireAfter(new Expiry<LimitKey, LimitCounter>() {
                    @Override
                    public long expireAfterCreate(LimitKey key, LimitCounter value, long currentTime) {
                        return value.intervalNanos();
                    }

                    @Override
                    public long expireAfterUpdate(
                            LimitKey key, LimitCounter value, long currentTime, long currentDuration) {
                        return value.intervalNanos();
                    }

                    @Override
                    public long expireAfterRead(
                            LimitKey key, LimitCounter value, long currentTime, long currentDuration) {
                        return value.intervalNanos();
                    }
                })
                .build();
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        if (ipBlacklist.isBlocked(identityResolver.resolveIp(request))) {
            writeError(response, HttpStatus.FORBIDDEN, "当前 IP 已被禁止访问");
            return false;
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

        HandlerKey handlerKey = new HandlerKey(handlerMethod.getBeanType(), handlerMethod.getMethod());
        LimitRule rule = rules.computeIfAbsent(handlerKey, ApiLimitInterceptor::resolveRule);
        if (rule == UNLIMITED) return true;

        String identity = identityResolver.resolve(request, rule.by());
        LimitKey key = new LimitKey(handlerKey, rule.by(), identity);
        LimitCounter counter = counters.get(
                key, ignored -> new LimitCounter(rule.count(), rule.intervalNanos()));
        long retryAfterNanos = counter.acquire(System.nanoTime());
        if (retryAfterNanos == 0) return true;

        writeRejected(response, retryAfterNanos);
        return false;
    }

    private static LimitRule resolveRule(HandlerKey handler) {
        ApiLimit annotation = AnnotatedElementUtils.findMergedAnnotation(handler.method(), ApiLimit.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handler.beanType(), ApiLimit.class);
        }
        if (annotation == null) return UNLIMITED;
        if (annotation.count() <= 0) {
            throw new IllegalStateException("@ApiLimit count must be positive: " + handler.description());
        }
        String configuredInterval = annotation.interval();
        if (configuredInterval == null || configuredInterval.isBlank()) {
            throw new IllegalStateException("@ApiLimit interval must not be blank: " + handler.description());
        }
        final Duration interval;
        try {
            interval = DurationStyle.detectAndParse(configuredInterval.trim());
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "Invalid @ApiLimit interval '" + configuredInterval + "': " + handler.description(), error);
        }
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalStateException("@ApiLimit interval must be positive: " + handler.description());
        }
        try {
            return new LimitRule(annotation.count(), interval.toNanos(), annotation.by());
        } catch (ArithmeticException error) {
            throw new IllegalStateException("@ApiLimit interval is too large: " + handler.description(), error);
        }
    }

    private static void writeRejected(HttpServletResponse response, long retryAfterNanos) throws IOException {
        long retryAfterSeconds = Math.max(1, Math.ceilDiv(retryAfterNanos, NANOS_PER_SECOND));
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        writeError(response, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
    }

    private static void writeError(
            HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"data\":null,\"code\":" + status.value()
                + ",\"crypt\":false,\"ok\":false,\"msg\":\"" + message
                + "\",\"timestamp\":"
                + System.currentTimeMillis() + "}");
    }

    private record HandlerKey(Class<?> beanType, Method method) {
        private String description() {
            return beanType.getName() + "#" + method.getName();
        }
    }

    private record LimitKey(HandlerKey handler, LimitBy by, String identity) {
    }

    private record LimitRule(int count, long intervalNanos, LimitBy by) {
    }

    /** One rolling fixed window; synchronization is local to one endpoint and one client. */
    private static final class LimitCounter {
        private final int limit;
        private final long intervalNanos;
        private long windowStartedAt = Long.MIN_VALUE;
        private int used;

        private LimitCounter(int limit, long intervalNanos) {
            this.limit = limit;
            this.intervalNanos = intervalNanos;
        }

        private long intervalNanos() {
            return intervalNanos;
        }

        /** Returns zero when accepted, otherwise nanoseconds until this window resets. */
        private synchronized long acquire(long now) {
            long elapsed = now - windowStartedAt;
            if (windowStartedAt == Long.MIN_VALUE || elapsed < 0 || elapsed >= intervalNanos) {
                windowStartedAt = now;
                used = 1;
                return 0;
            }
            if (used < limit) {
                used++;
                return 0;
            }
            return intervalNanos - elapsed;
        }
    }
}

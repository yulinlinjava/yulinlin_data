package com.yulinlin.mcp.token;

import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.*;

/**
 * 默认认证器：{@code Authorization: Bearer ylmcp_{keyId}_{secret}}。
 * 按 keyId 查找（全量内存缓存）→ SHA-256(secret) 常量时间比较 → 校验状态、过期、IP 白名单、限流。
 * <p>
 * IP 取 {@code request.getRemoteAddr()}；部署在反向代理后时，需要配置 {@code server.forward-headers-strategy}，
 * 否则拿到的是代理地址。
 */
public class TokenAuthenticator implements McpAuthenticator {
    public static final String TYPE = "token";
    private final McpTokenService tokens;
    private final Clock clock;
    private final Map<String, long[]> windows = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, long[]> eldest) { return size() > 10_000; }
    };

    public TokenAuthenticator(McpTokenService tokens) { this(tokens, Clock.systemUTC()); }

    public TokenAuthenticator(McpTokenService tokens, Clock clock) {
        this.tokens = Objects.requireNonNull(tokens);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public String type() { return TYPE; }

    @Override
    public McpPrincipal authenticate(HttpServletRequest request) {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) return null;
        if (headers.size() != 1) throw McpAuthentication.unauthorized();
        String header = headers.getFirst();
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        var parsed = McpTokenCodec.parse(header.substring(7).strip());
        if (parsed == null) return null; // 不是本格式，交给其他认证器
        long now = clock.millis();
        var token = tokens.findByKeyId(parsed.keyId());
        if (token == null || !token.matches(McpTokenCodec.hash(parsed.secret()), now) || !token.usable(now))
            throw McpAuthentication.unauthorized();
        String ip = request.getRemoteAddr();
        if (!McpIpRules.allows(token.allowedIps(), ip)) throw new McpException(403, "当前来源 IP 不允许使用此令牌");
        if (!acquire(token, now)) throw new McpException(429, "调用过于频繁，请稍后再试");
        tokens.touch(token, ip);
        return principal(token);
    }

    @Override
    public McpPrincipal refresh(McpPrincipal principal) {
        var token = tokens.findById(principal.credentialId());
        if (token == null || !token.usable(clock.millis())) return null;
        return principal(token);
    }

    private static McpPrincipal principal(McpToken token) {
        return new McpPrincipal(TYPE, token.id(), token.name(), token.ownerId(), token.scope(), token.attributes());
    }

    /** 单实例固定窗口限流（次/分钟）。多实例时每个实例分别计数。 */
    private boolean acquire(McpToken token, long now) {
        int limit = token.rateLimit();
        if (limit <= 0) return true;
        long window = now / 60_000L;
        synchronized (windows) {
            var counter = windows.computeIfAbsent(token.id(), k -> new long[]{window, 0});
            if (counter[0] != window) { counter[0] = window; counter[1] = 0; }
            if (counter[1] >= limit) return false;
            counter[1]++;
            return true;
        }
    }
}

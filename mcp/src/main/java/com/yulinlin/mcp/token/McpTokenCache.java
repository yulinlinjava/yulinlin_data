package com.yulinlin.mcp.token;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌全量内存缓存。令牌数量很少，直接整表加载，认证时不查库；不存在的 keyId 也不会打到数据库。
 * <ul>
 *   <li>每隔 ttl 重新加载一次，多实例下其他实例的修改最多延迟 ttl 可见；</li>
 *   <li>本实例修改后调用 {@link #invalidate}，下一次认证立即重新加载；</li>
 *   <li>ttl &lt;= 0 时不缓存，每次直接按 keyId 查库。</li>
 * </ul>
 */
public class McpTokenCache {
    private static final int PAGE = 200;
    private final McpTokenStore store;
    private final long ttlMillis;
    private final Clock clock;
    /** 每次本地修改加一；加载时记下开始时的代数，加载期间发生的修改不会被覆盖掉。 */
    private final AtomicLong generation = new AtomicLong(1);
    private volatile long loadedGeneration;
    private volatile long loadedAt;
    private volatile Map<String, McpToken> byKeyId = Map.of();

    public McpTokenCache(McpTokenStore store, long ttlMillis, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.ttlMillis = ttlMillis;
        this.clock = Objects.requireNonNull(clock);
    }

    public McpToken get(String keyId) {
        if (ttlMillis <= 0) return store.findByKeyId(keyId);
        if (stale(clock.millis())) reload();
        return byKeyId.get(keyId);
    }

    /** 本实例修改了令牌：下次读取时整表重新加载。 */
    public void invalidate(String keyId) { generation.incrementAndGet(); }

    private boolean stale(long now) { return loadedGeneration != generation.get() || now - loadedAt >= ttlMillis; }

    private synchronized void reload() {
        long now = clock.millis();
        if (!stale(now)) return; // 其他线程刚加载过
        long startGeneration = generation.get();
        var all = new HashMap<String, McpToken>();
        for (int page = 1; ; page++) {
            var rows = store.list(new McpTokenQuery(null, null, null, page, PAGE, now));
            for (var token : rows) all.put(token.keyId(), token);
            if (rows.size() < PAGE) break;
        }
        byKeyId = Map.copyOf(all);
        loadedAt = now;
        loadedGeneration = startGeneration;
    }
}

package com.yulinlin.data.core.loadbalan;

import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RandomLoadBalance extends AbstractLoadBalance {
    private record HealthSnapshot(Map<String, List<LoadBalanceNode>> registered,
                                  Map<String, List<LoadBalanceNode>> healthy) { }
    private volatile HealthSnapshot healthSnapshot;

    @Override
    protected Map<String, List<LoadBalanceNode>> getCache() {
        Map<String, List<LoadBalanceNode>> registered = super.getCache();
        HealthSnapshot health = healthSnapshot;
        return health != null && health.registered() == registered ? health.healthy() : registered;
    }

    @Override
    public void ping() {
        Map<String, List<LoadBalanceNode>> registered = super.getCache();
        Map<String, List<LoadBalanceNode>> healthy = new LinkedHashMap<>();
        for (var entry : registered.entrySet()) {
            List<LoadBalanceNode> alive = new ArrayList<>();
            for (LoadBalanceNode node : entry.getValue()) {
                try {
                    if (node.ping()) alive.add(node);
                } catch (Exception error) {
                    log.warn("会话心跳失败，group={}, type={}", entry.getKey(), error.getClass().getName());
                }
            }
            // Keep offline group names: do not silently redirect a default database to another one.
            healthy.put(entry.getKey(), List.copyOf(alive));
        }
        synchronized (this) {
            if (super.getCache() == registered) {
                healthSnapshot = new HealthSnapshot(registered, Collections.unmodifiableMap(healthy));
            }
        }
    }
}

package com.yulinlin.data.core.loadbalan;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.exception.NoticeException;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public abstract class AbstractLoadBalance implements LoadBalance {
    /** Immutable registration snapshots: readers do not lock or iterate a mutating collection. */
    protected volatile Map<String, List<LoadBalanceNode>> map = Collections.emptyMap();
    private volatile String defaultGroup;

    protected Map<String, List<LoadBalanceNode>> getCache() {
        return map;
    }

    @Override
    public String getDefaultGroup() {
        return defaultGroup;
    }

    @Override
    public void setDefaultGroup(String group) {
        defaultGroup = group == null || group.isBlank() ? null : group;
    }

    @Override
    public String defaultGroup() {
        return resolveDefaultGroup(getCache());
    }

    private String resolveDefaultGroup(Map<String, List<LoadBalanceNode>> cache) {
        if (cache.isEmpty()) throw new NoticeException("数据源请注册");
        // A single registered group does not need configuration, even when it contains replica nodes.
        if (cache.size() == 1) return cache.keySet().iterator().next();
        String configured = defaultGroup;
        if (configured == null) {
            throw new NoticeException("多个会话组请设置 yulinlin.datasource.default-group，或显式指定 group：" + cache.keySet());
        }
        if (!cache.containsKey(configured)) throw new NoticeException("默认会话组未注册：" + configured);
        return configured;
    }

    @Override
    public LoadBalanceNode loadBalance(String group, JoinCluster tag) {
        Map<String, List<LoadBalanceNode>> cache = getCache();
        String selectedGroup = group == null || group.isBlank() ? resolveDefaultGroup(cache) : group;
        List<LoadBalanceNode> nodes = cache.get(selectedGroup);
        if (nodes == null || nodes.isEmpty()) throw unavailable(selectedGroup, tag);

        if (nodes.size() == 1) {
            LoadBalanceNode node = nodes.getFirst();
            if ((tag == null || node.cluster() == tag) && checkedWeight(node) > 0) return node;
            throw unavailable(selectedGroup, tag);
        }

        // Sample each weight once; avoid streams and preserve this request's weighted selection snapshot.
        int[] weights = new int[nodes.size()];
        long total = 0;
        int eligible = 0;
        LoadBalanceNode only = null;
        for (int i = 0; i < nodes.size(); i++) {
            LoadBalanceNode node = nodes.get(i);
            if (tag != null && node.cluster() != tag) continue;
            int weight = checkedWeight(node);
            if (weight == 0) continue;
            weights[i] = weight;
            total += weight;
            eligible++;
            only = node;
        }
        if (eligible == 0) throw unavailable(selectedGroup, tag);
        if (eligible == 1) return only;

        long offset = randomWeight(total); // [0, total), not the biased inclusive [0, total].
        for (int i = 0; i < weights.length; i++) {
            if (offset < weights[i]) return nodes.get(i);
            offset -= weights[i];
        }
        throw new IllegalStateException("Invalid weighted selection for group: " + selectedGroup);
    }

    protected long randomWeight(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }

    private int checkedWeight(LoadBalanceNode node) {
        int weight = node.weight();
        if (weight < 0) throw new IllegalArgumentException("节点权重不能为负数，group=" + node.group());
        return weight;
    }

    private NoticeException unavailable(String group, JoinCluster tag) {
        return new NoticeException("会话组不存在或无符合条件的可用节点：" + group + ", cluster=" + tag);
    }

    @Override
    public Set<String> loadBalanceList() {
        return getCache().keySet();
    }

    @Override
    public synchronized void register(LoadBalanceNode session) {
        Objects.requireNonNull(session, "session");
        String group = session.group();
        if (group == null || group.isBlank()) throw new IllegalArgumentException("会话组不能为空");
        checkedWeight(session);
        List<LoadBalanceNode> existing = map.get(group);
        if (existing != null && existing.contains(session)) return;
        List<LoadBalanceNode> nodes = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        nodes.add(session);
        Map<String, List<LoadBalanceNode>> next = new LinkedHashMap<>(map);
        next.put(group, List.copyOf(nodes));
        map = Collections.unmodifiableMap(next);
    }

    @Override
    public boolean remove(LoadBalanceNode session) {
        if (session == null) return false;
        boolean removed = false;
        synchronized (this) {
            // Find by identity in case a mutable node changed its group after registration.
            Map<String, List<LoadBalanceNode>> next = new LinkedHashMap<>(map);
            for (var entry : map.entrySet()) {
                List<LoadBalanceNode> nodes = new ArrayList<>(entry.getValue());
                if (nodes.removeIf(node -> node == session)) {
                    if (nodes.isEmpty()) next.remove(entry.getKey());
                    else next.put(entry.getKey(), List.copyOf(nodes));
                    removed = true;
                }
            }
            if (removed) map = Collections.unmodifiableMap(next);
        }
        if (removed) {
            try { session.shutdown(); }
            catch (Exception error) { log.error("session关闭异常", error); }
        }
        return removed;
    }

    /** Retain the existing hook without starting a background scheduler. */
    public void heartbeat(int ttl) { }
}

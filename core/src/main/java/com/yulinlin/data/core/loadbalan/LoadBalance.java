package com.yulinlin.data.core.loadbalan;


import com.yulinlin.data.core.anno.JoinCluster;

import java.util.Set;

public interface LoadBalance {

    Set<String> loadBalanceList();

    <E extends LoadBalanceNode> E loadBalance(String group,JoinCluster tag);

    void register( LoadBalanceNode session);

    boolean remove(LoadBalanceNode session);

    String defaultGroup();

    /** Configured group for requests without a selector; the effective single-group default may differ. */
    default String getDefaultGroup() { return null; }

    default void setDefaultGroup(String group) {
        throw new UnsupportedOperationException("This load balancer does not support a configurable default group");
    }


    /**
     * 检测那些会话存活，不存活的取消负载均衡
     */
    void ping();

}

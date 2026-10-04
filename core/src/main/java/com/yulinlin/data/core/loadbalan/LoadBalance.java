package com.yulinlin.data.core.loadbalan;


import com.yulinlin.data.core.anno.JoinCluster;

import java.util.Set;

public interface LoadBalance {

    Set<String> loadBalanceList();

    <E extends LoadBalanceNode> E loadBalance(String group,JoinCluster tag);

    void register( LoadBalanceNode session);

    boolean remove(LoadBalanceNode session);



    /**
     * 检测那些会话存活，不存活的取消负载均衡
     */
    void ping();

}

package com.yulinlin.data.core.loadbalan;

import com.yulinlin.data.core.anno.JoinCluster;

import java.io.Closeable;
import java.io.IOException;

public interface LoadBalanceNode  {

    //分组
    String group();

    //标签
    JoinCluster cluster();

    //权重
    int weight();

    /**
     * 是否存活
     * @return
     */
    boolean ping();

    void shutdown();
}

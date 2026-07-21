package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.loadbalan.LoadBalanceNode;

public abstract class LoadBalanceSession implements LoadBalanceNode {

    private String group;

    private JoinCluster cluster;

    private int weight;

    public LoadBalanceSession() {
        this.cluster = JoinCluster.master;
        this.group="default";
        this.weight = 1;
    }


    @Override
    public String group() {
        return group;
    }

    @Override
    public JoinCluster cluster() {
        return cluster;
    }

    @Override
    public int weight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setCluster(JoinCluster cluster) {
        this.cluster = cluster;
    }
}

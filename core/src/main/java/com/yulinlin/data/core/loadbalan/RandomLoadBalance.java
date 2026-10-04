package com.yulinlin.data.core.loadbalan;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RandomLoadBalance extends AbstractLoadBalance {

    protected Map<String,List<LoadBalanceNode>> pingMap ;


    @Override
    protected Map<String, List<LoadBalanceNode>> getCache() {
        if(pingMap != null){
            return pingMap;
        }
        return super.getCache();
    }

    @Override
    public void ping() {
        Map<String,List<LoadBalanceNode>>  ping = new HashMap<>();
        for (Map.Entry<String, List<LoadBalanceNode>> entry : map.entrySet()) {
            List<LoadBalanceNode> list = entry.getValue().stream().filter(row -> row.ping()).collect(Collectors.toList());
            ping.put(entry.getKey(),list);
        }
        this.pingMap = ping;
    }


    @Override
    public void register(LoadBalanceNode session) {
        super.register(session);
        pingMap = null;
    }

    @Override
    public boolean remove(LoadBalanceNode session) {
        pingMap = null;
        return super.remove(session);
    }
}

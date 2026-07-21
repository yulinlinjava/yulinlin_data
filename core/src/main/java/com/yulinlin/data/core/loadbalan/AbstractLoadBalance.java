package com.yulinlin.data.core.loadbalan;


import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.lang.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractLoadBalance implements LoadBalance {

    protected Map<String,List<LoadBalanceNode>> map = new HashMap<>();

    protected Map<String, List<LoadBalanceNode>> getCache() {
        return map;
    }

    private Random random =  new Random();

    @Override
    public LoadBalanceNode loadBalance(String group,JoinCluster tag) {

        List<LoadBalanceNode> list =   getCache().get(group);
        if(list == null || list.isEmpty()){
            throw new NoticeException("会话组不存在："+group);
        }
        if(tag != null){
            list =  list.stream().filter(row -> row.cluster() == tag).collect(Collectors.toList());
        }

        if(list.size() == 1){
            return list.get(0);
        }
        int total = 0;
        for (LoadBalanceNode loadBalanceNode : list) {
            total += loadBalanceNode.weight();
        }

        int weight =random.nextInt(total+1);
        for (LoadBalanceNode loadBalanceNode : list) {
            weight-=loadBalanceNode.weight();
            if(weight <= 0){
                return loadBalanceNode;
            }
        }
        throw new RuntimeException("回去会话失败，会话不存在或心跳异常："+group+"_"+tag);
    }

    @Override
    public Set<String> loadBalanceList() {
        return getCache().keySet();
    }

    @Override
    public void register( LoadBalanceNode session) {
        List<LoadBalanceNode> list = map.computeIfAbsent(session.group(),(k) -> {
            return new ArrayList<>();
        });
        if(!list.contains(session)){
            list.add(session);
        }

    }


    @Override
    public boolean remove(LoadBalanceNode session) {

        try {
            session.shutdown();
        }catch (Exception e){
            log.error("session关闭异常",e);
        }


        List<LoadBalanceNode> list =  map.get(session.group());
        if(list != null){

              return   list.removeIf((s) -> {
                    return s == session;
                });
        }
        return false;
    }


    private         ScheduledFuture scheduledFuture;

    public  synchronized     void heartbeat(int ttl){

        if(scheduledFuture != null){
            scheduledFuture.cancel(true);
        }
         scheduledFuture =  ThreadUtil.schedule(() -> {
            ping();
        },ttl*1000);

    }
}

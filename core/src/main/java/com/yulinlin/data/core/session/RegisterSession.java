package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.wrapper.IWrapperFactory;
import lombok.SneakyThrows;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class RegisterSession {


    private LoadBalance loadBalance;

    private IWrapperFactory wrapperFactory;

    private QueryCache queryCache = NoOpQueryCache.INSTANCE;


    public IWrapperFactory getWrapperFactory() {
        return wrapperFactory;
    }

    // 无事务时也可使用的选库作用域，不是事务上下文。
    private final ThreadLocal<LinkedList<EntitySession>> threadLocal= ThreadLocal.withInitial(()  -> {
        return  new LinkedList<>();
    });

    public  void registerSession(List<EntitySession> list){
        for (EntitySession session : list) {
            registerSession(session);
        }
    }

    public  void registerSession(EntitySession session){
        session.setQueryCache(queryCache);
        loadBalance.register(session);
    }

    public void setQueryCache(QueryCache queryCache) {
        this.queryCache = queryCache == null ? NoOpQueryCache.INSTANCE : queryCache;
    }

    protected QueryCache queryCache() {
        return queryCache;
    }

    protected EntitySession enlist(EntitySession session) {
        return session;
    }

    /** 基础注册器只选库；RouteSession 覆盖此入口维护事务内的节点亲和性。 */
    protected EntitySession resolveSession(String group, JoinCluster cluster) {
        return loadBalance.loadBalance(group, cluster);
    }

    @SneakyThrows
    public  void remove(EntitySession session){
        loadBalance.remove(session);
    }


    public  void pushSession(String code) {
        pushSession(code,JoinCluster.master);
    }
    public  void pushSession(EntitySession session) {
        threadLocal.get().addFirst(session);
    }
    public  void pushSession(String code, JoinCluster tag){
        EntitySession session  =  session(code,tag);
        threadLocal.get().addFirst(session);
    }

    public  void popSession(){
        threadLocal.get().removeFirst();
    }

    public  void clear(){
    }


    public  EntitySession session(){
        return session(null,JoinCluster.master);
    }

    public  EntitySession session(String code){

        return session(code,JoinCluster.master);
    }

    public  EntitySession session(String code,JoinCluster tag){

        if(code == null || code.isBlank()){
            if(threadLocal.get().size() > 0){
               return enlist(threadLocal.get().getFirst());
            }else {
                code = defaultSessionGroup();
            }
        }
        if(tag == null){
            tag = JoinCluster.master;
        }

        EntitySession session = resolveSession(code, tag);

        return enlist(session);

    }



    private String defaultSessionGroup() {
        return loadBalance.defaultGroup();
    }

    public  int sessionSize(){
        return loadBalance.loadBalanceList().size();
    }

    public Set<String> loadBalanceList(){
        return loadBalance.loadBalanceList();
    }

    public void setLoadBalance(LoadBalance loadBalance) {
        this.loadBalance = loadBalance;
    }

    public void setWrapperFactory(IWrapperFactory wrapperFactory) {
        this.wrapperFactory = wrapperFactory;
    }
}

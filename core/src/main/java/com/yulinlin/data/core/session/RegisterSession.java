package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.wrapper.IWrapperFactory;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class RegisterSession extends BaseTransactionSession{

    public  static String master ="primary";

    private LoadBalance loadBalance;

    private IWrapperFactory wrapperFactory;


    public IWrapperFactory getWrapperFactory() {
        return wrapperFactory;
    }

    private static ThreadLocal<HashMap<String,EntitySession>> cacheSession= ThreadLocal.withInitial(()  -> {
        return  new HashMap<>();
    });

    private  static ThreadLocal<LinkedList<EntitySession>> threadLocal= ThreadLocal.withInitial(()  -> {
        return  new LinkedList<>();
    });


    public  void registerSession(List<EntitySession> list){
        for (EntitySession session : list) {
            registerSession(session);
        }
    }

    public  void registerSession(EntitySession session){
        loadBalance.register(session);
        if(master == null){
            master = session.group();
        }
    }

    @Override
    public void commitTransaction() {
        boolean ok = isOpenTransaction();
        super.commitTransaction();
        if(ok && !isOpenTransaction()){
            HashMap<String, EntitySession> data = cacheSession.get();

            for (EntitySession session : data.values()) {
                session.commitTransaction();
            }

            clear();
        }

    }

    @Override
    public void rollbackTransaction() {
        boolean ok = isOpenTransaction();
        super.rollbackTransaction();
        if(ok && !isOpenTransaction()){
            for (EntitySession session : cacheSession.get().values()) {
                session.rollbackTransaction();
            }

            clear();
        }

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
        cacheSession.get().clear();
    }


    public  EntitySession session(){
        return session(null,JoinCluster.master);
    }

    public  EntitySession session(String code){

        return session(code,JoinCluster.master);
    }

    public  EntitySession session(String code,JoinCluster tag){

        if(code == null || code.isEmpty()){
            if(threadLocal.get().size() > 0){
               return threadLocal.get().getFirst();
            }else {
                code = master;
            }
        }
        if(tag == null){
            tag = JoinCluster.master;
        }

        String group  = code;
        JoinCluster cluster = tag;
        HashMap<String, EntitySession> map = cacheSession.get();

        EntitySession session =  map.computeIfAbsent(code,k -> {
           return loadBalance.loadBalance(group,cluster);
        });

        return session;

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

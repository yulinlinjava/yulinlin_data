package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.wrapper.IWrapperFactory;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RegisterSession extends BaseTransactionSession{


    private LoadBalance loadBalance;

    private IWrapperFactory wrapperFactory;

    private QueryCache queryCache = NoOpQueryCache.INSTANCE;


    public IWrapperFactory getWrapperFactory() {
        return wrapperFactory;
    }

    private final ThreadLocal<HashMap<String,EntitySession>> cacheSession= ThreadLocal.withInitial(()  -> {
        return  new HashMap<>();
    });

    private final ThreadLocal<LinkedList<EntitySession>> threadLocal= ThreadLocal.withInitial(()  -> {
        return  new LinkedList<>();
    });

    private final ThreadLocal<Set<EntitySession>> participants = ThreadLocal.withInitial(LinkedHashSet::new);


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

    @Override
    public void commitTransaction() {
        boolean ok = isOpenTransaction();
        boolean rollback = isRollbackOnly();
        super.commitTransaction();
        if(ok && !isOpenTransaction()){
            finishParticipants(rollback);
            if (rollback) throw new IllegalStateException("Route transaction was marked rollback-only");
        }

    }

    @Override
    public void rollbackTransaction() {
        boolean ok = isOpenTransaction();
        setRollbackOnly();
        super.rollbackTransaction();
        if(ok && !isOpenTransaction()){
            finishParticipants(true);
        }

    }

    @Override
    public void setRollbackOnly() {
        super.setRollbackOnly();
        for (EntitySession session : participants.get()) session.setRollbackOnly();
    }

    @Override
    public boolean isRollbackOnly() {
        return super.isRollbackOnly() || participants.get().stream().anyMatch(EntitySession::isRollbackOnly);
    }

    protected EntitySession enlist(EntitySession session) {
        if (isOpenTransaction() && !participants.get().contains(session)) {
            // Nest once even when an independently started session transaction already exists.
            session.startTransaction();
            participants.get().add(session);
            if (isRollbackOnly()) session.setRollbackOnly();
        }
        return session;
    }

    @SneakyThrows
    private void finishParticipants(boolean rollback) {
        Throwable failure = null;
        try {
            for (EntitySession session : participants.get()) {
                try {
                    if (rollback || failure != null) session.rollbackTransaction();
                    else session.commitTransaction();
                } catch (Throwable error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                    // A third-party participant may leave resources open after commit fails.
                    try { session.rollbackTransaction(); }
                    catch (Throwable cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
                }
            }
        } finally {
            participants.remove();
            clear();
        }
        if (failure != null) throw failure;
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
        cacheSession.remove();
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

        String group  = code;
        JoinCluster cluster = tag;
        EntitySession session;
        if (isOpenTransaction()) {
            session = cacheSession.get().computeIfAbsent(code + ":" + cluster.name(),
                    key -> loadBalance.loadBalance(group, cluster));
        } else {
            // Do not retain an old node indefinitely after weights or health snapshots change.
            session = loadBalance.loadBalance(group, cluster);
        }

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

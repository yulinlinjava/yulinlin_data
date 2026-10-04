package com.yulinlin.data.core.request;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExecuteRequest<E> extends BaseRequest<E> {

    private RequestType requestType;



    private List<INode> wrappers = new ArrayList<>();

    private boolean batch;


    private int batchSize = 128;

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    private ExecuteRequest(Class entityClass) {
        super(entityClass);
    }

    private ExecuteRequest(Class entityClass, Class fromClass) {
        super(entityClass, fromClass);
    }

    public boolean isBatch() {
        return batch;
    }

    public ExecuteRequest copy(INode wrapper){
        return new ExecuteRequest(this.getEntityClass(),this.getFromClass())
                .addRequest(wrapper);
    }

    public ExecuteRequest<E> addRequest(INode node){
        wrappers.add(node);
        return this;
    }



    public Integer execute(){
        if(wrappers.size() == 0){
            return 0;
        }
        if(requestType == RequestType.insert){
            return SessionUtil.route().insert(this);
        }else if(requestType == RequestType.update){
            return SessionUtil.route().update(this);
        }else if(requestType == RequestType.delete){
            return SessionUtil.route().delete(this);
        }
        return null;
    }


    public RequestType getRequestType() {
        return requestType;
    }

    public List<INode> getWrappers() {
        return wrappers;
    }



    private static ExecuteRequest newInstance(Class clazz, RequestType requestType){
        ExecuteRequest executeRequest = new ExecuteRequest(clazz,clazz);
        executeRequest.requestType = requestType;
        return executeRequest;
    }

    public static ExecuteRequest ofInsert(Class clazz){
        return newInstance(clazz,RequestType.insert);
    }

    public static ExecuteRequest ofUpdate(Class clazz){
        return newInstance(clazz,RequestType.update);
    }

    public static ExecuteRequest ofDelete(Class clazz){
        return newInstance(clazz,RequestType.delete);
    }
}

package com.yulinlin.data.core.request;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecuteRequest<E> extends BaseRequest<E> {

    private RequestType requestType;



    private List<INode> wrappers = new ArrayList<>();

    private boolean batch;

    /** Null inherits {@code yulinlin.cache.clear-on-write}; non-null is a per-request override. */
    private Boolean clearCache;

    /** Delays this write until the framework write queue is flushed. */
    private boolean queued;

    public void setBatch(boolean batch) {
        this.batch = batch;
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

    public Boolean getClearCache() {
        return clearCache;
    }

    public ExecuteRequest<E> clearCache(boolean clearCache) {
        this.clearCache = clearCache;
        return this;
    }

    public void setClearCache(Boolean clearCache) {
        this.clearCache = clearCache;
    }

    public boolean shouldClearCache(boolean defaultValue) {
        return clearCache == null ? defaultValue : clearCache;
    }

    public ExecuteRequest<E> queue() {
        this.queued = true;
        return this;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }

    public ExecuteRequest copy(INode wrapper){
        ExecuteRequest copy = new ExecuteRequest(this.getEntityClass(),this.getFromClass())
                .addRequest(wrapper);
        copyExecutionOptionsTo(copy, true);
        return copy;
    }

    /**
     * Creates a queue-owned request from the already generated nodes. The source entity/root is deliberately
     * not retained: queued writes use the values captured by the nodes when the wrapper was created.
     */
    public ExecuteRequest<E> queuedSnapshot() {
        ExecuteRequest<E> copy = new ExecuteRequest<>(getEntityClass(), getFromClass());
        // Queued writes are grouped by fromClass; normalize non-semantic write entityClass differences.
        if (getFromClass() != null) copy.setEntityClass(getFromClass());
        for (INode wrapper : wrappers) copy.wrappers.add(snapshotNode(wrapper));
        copyExecutionOptionsTo(copy, false);
        copy.queued = false;
        return copy;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static INode snapshotNode(INode node) {
        if (node instanceof CommandNode command && command.getParams() != null) {
            return new CommandNode(command.getExpression(),
                    new LinkedHashMap<>(command.getParams()), command.getType());
        }
        return node;
    }

    /** Adds nodes and cache invalidation metadata from a compatible queued request. */
    public void mergeQueued(ExecuteRequest<?> other) {
        if (other == null) return;
        if (requestType != other.requestType
                || getFromClass() != other.getFromClass()) {
            throw new IllegalArgumentException("Only compatible queued requests can be merged");
        }
        wrappers.addAll(other.wrappers);
        // Execution tuning is not part of the queue grouping key. Use parallel execution when any request asks for it.
        batch |= other.batch;
        clearCache = mergeClearCache(clearCache, other.clearCache);
        addCacheNamespaces(other.getCacheNamespaces().toArray(String[]::new));
    }

    private static Boolean mergeClearCache(Boolean first, Boolean second) {
        if (Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second)) return true;
        if (first == null || second == null) return null;
        return false;
    }

    /** Preserves caller options when a request filter replaces the write request. */
    public void inheritExecutionOptions(ExecuteRequest<?> source) {
        if (source == null || source == this) return;
        batch = source.batch;
        // A caller override wins; otherwise keep a replacement filter's explicit policy.
        if (source.clearCache != null) clearCache = source.clearCache;
        queued = source.queued;
        addCacheNamespaces(source.getCacheNamespaces().toArray(String[]::new));
        if (getSession() == null || getSession().isBlank()) setSession(source.getSession());
        if (getCluster() == null) setCluster(source.getCluster());
    }

    private void copyExecutionOptionsTo(ExecuteRequest copy, boolean includeRoot) {
        copy.requestType = requestType;
        if (includeRoot) copy.setRoot(getRoot());
        copy.setSession(getSession());
        copy.setCluster(getCluster());
        copy.batch = batch;
        copy.clearCache = clearCache;
        copy.queued = queued;
        copy.addCacheNamespaces(getCacheNamespaces().toArray(String[]::new));
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

    public static <E> ExecuteRequest<E> newInstance(String sql, Map<String,Object> params){
        ExecuteRequest<E> executeRequest = new ExecuteRequest<>(Object.class, Object.class);
        executeRequest.requestType = RequestType.update;
        executeRequest.addRequest(
                new CommandNode(sql, params, ParseType.update)
        );
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

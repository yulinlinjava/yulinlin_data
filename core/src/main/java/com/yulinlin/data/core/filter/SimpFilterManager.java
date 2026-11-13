package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class SimpFilterManager implements IFilterManager {

    /** 注册时复制并排序，执行时只读数组快照，无锁且不创建迭代器。 */
    private volatile IFilter[] listeners = new IFilter[0];

    public SimpFilterManager() {

    }

    public SimpFilterManager(Collection<? extends IFilter> listeners) {
        this();
        register(listeners);
    }

    //注册一个监听器
    public void register(IFilter listener){
        register(List.of(Objects.requireNonNull(listener, "filter")));
    }

    public synchronized void register(Collection<? extends IFilter> additions){
        Objects.requireNonNull(additions, "filters");
        if (additions.isEmpty()) return;
        IFilter[] extra = additions.toArray(IFilter[]::new);
        for (IFilter filter : extra) {
            Objects.requireNonNull(filter, "filter");
            if (!(filter instanceof IQueryFilter) && !(filter instanceof IWriteFilter))
                throw new IllegalArgumentException("过滤器必须实现 IQueryFilter 或 IWriteFilter");
        }
        IFilter[] next = Arrays.copyOf(listeners, listeners.length + extra.length);
        System.arraycopy(extra, 0, next, listeners.length, extra.length);
        Arrays.sort(next, Comparator.comparingInt(IFilter::order));
        listeners = next;
    }


    @Override
    public QueryRequest<?> beforeQuery(String session, QueryRequest<?> request) {
        for (IFilter listener : listeners) {
            if (listener instanceof IQueryFilter query && query.test(session, request)) {
                request = query.beforeQuery(session, request);
                if (request == null)
                    throw new IllegalStateException("查询过滤器必须返回非 null 的 QueryRequest");
            }
        }
        return request;
    }

    @Override
    public ExecuteRequest<?> beforeWrite(String session, ExecuteRequest<?> request) {
        for (IFilter listener : listeners) {
            if (listener instanceof IWriteFilter write && write.test(session, request)) {
                request = write.beforeWrite(session, request);
                if (request == null)
                    throw new IllegalStateException("写入过滤器必须返回非 null 的 ExecuteRequest");
            }
        }
        return request;
    }

    @Override
    public void checkSelectEach(String session, QueryRequest<?> request) {
        for (IFilter listener : listeners) {
            if (listener instanceof IQueryFilter query
                    && query.test(session, request) && !query.supportsSelectEach())
                throw new UnsupportedOperationException("过滤器 " + listener.getClass().getName()
                        + " 不支持 selectEach");
        }
    }

    @Override
    public void afterRow(String session, QueryRequest<?> request, Object row) {
        for (IFilter listener : listeners) {
            if (listener instanceof IQueryFilter query && listener.test(session, request)) {
                query.afterRow(session, request, row);
            }
        }
    }

    @Override
    public void afterWrite(String session, ExecuteRequest<?> request, int affectedRows) {
        for (IFilter listener : listeners) {
            if (listener instanceof IWriteFilter write && listener.test(session, request)) {
                write.afterWrite(session, request, affectedRows);
            }
        }
    }
}

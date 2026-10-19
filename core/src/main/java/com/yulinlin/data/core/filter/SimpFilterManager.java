package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;

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

    public SimpFilterManager(Collection<IFilter> listeners) {
        this();
        register(listeners);
    }

    //注册一个监听器
    public void register(IFilter listener){
        register(List.of(Objects.requireNonNull(listener, "filter")));
    }

    public synchronized void register(Collection<IFilter> additions){
        Objects.requireNonNull(additions, "filters");
        if (additions.isEmpty()) return;
        IFilter[] extra = additions.toArray(IFilter[]::new);
        for (IFilter filter : extra) Objects.requireNonNull(filter, "filter");
        IFilter[] next = Arrays.copyOf(listeners, listeners.length + extra.length);
        System.arraycopy(extra, 0, next, listeners.length, extra.length);
        Arrays.sort(next, Comparator.comparingInt(IFilter::order));
        listeners = next;
    }


    @Override
    public BaseRequest before(String session, BaseRequest request){
        Objects.requireNonNull(request, "request");
        for (IFilter listener : listeners) {
            if(listener.test(session,request)){
                request = Objects.requireNonNull(listener.before(session,request),
                        "before 过滤器不能返回 null 请求");
            }
        }
        return request;

    }


    @Override
    public Object after(String session, BaseRequest request, Object data) {
        Objects.requireNonNull(request, "request");
        for (IFilter listener : listeners) {
            if(listener.test(session,request)){
                data =  listener.after(session,request,data);
            }
        }
        return data;
    }
}

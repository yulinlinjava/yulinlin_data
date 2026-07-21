package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SimpFilterManager implements IFilterManager {

    private List<IFilter> listeners;

    public SimpFilterManager() {
        this.listeners = new ArrayList<>();

    }

    public SimpFilterManager(Collection<IFilter> listeners) {
        this();
        register(listeners);
    }

    //注册一个监听器
    public void register(IFilter listener){
        register(Arrays.asList(listener));
    }

    public void register(Collection<IFilter> listener){
        this.listeners.addAll(listener);
        listeners.sort((o1, o2) -> o1.order() - o2.order());
    }


    //arrayList.sort((o1, o2) -> o1 - o2);
@Override
    //查询监听
    public BaseRequest before(String session, BaseRequest request){

        for (IFilter listener : listeners) {
            if(listener.test(session,request)){
                request = listener.before(session,request);
            }
        }
        return request;

    }


    @Override
    public Object after(String session, BaseRequest request, Object data) {
        for (IFilter listener : listeners) {
            if(listener.test(session,request)){
                data =  listener.after(session,request,data);
            }
        }
        return data;
    }
}

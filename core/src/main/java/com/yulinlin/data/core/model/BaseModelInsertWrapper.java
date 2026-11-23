package com.yulinlin.data.core.model;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IDeleteWrapper;
import com.yulinlin.data.core.wrapper.IInsertWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BaseModelInsertWrapper<E> {

    private IInsertWrapper wrapper;

    private ExecuteRequest<?> request;

    private String session;

    public BaseModelInsertWrapper(String session,Object model) {
        this.session = session;
        init(session,model);
    }


    private void init(String session, Object list){
        if(list instanceof Collection){
            for (Object o : (Collection)list) {
                initOne(session,o);
            }
        }else {
            initOne(session,list);
        }

    }

    public BaseModelInsertWrapper<E> batch(boolean batch){
        request.setBatch(batch);
        return this;
    }

    public BaseModelInsertWrapper<E> batch(){
        return batch(true);
    }

    /** Enqueues this write and returns {@code 0} immediately after successful submission. */
    public BaseModelInsertWrapper<E> queue(){
        if (request != null) request.queue();
        return this;
    }

    private void addRequest( Object list){
        if(list instanceof Collection){
            for (Object o : (Collection)list) {
                initOne(session,o);
            }
        }else {
            initOne(session,list);
        }
    }
    private void initOne(String session, Object obj){
        if(obj == null){
            return;
        }
        if(obj instanceof Class){
            obj  = ReflectionUtil.newInstance((Class) obj);
        }

        IInsertWrapper node =  SessionUtil.route().getWrapperFactory().createInsertWrapper(obj);

        if(request == null){
            this.request = ExecuteRequest.ofInsert(obj.getClass());
            this.wrapper = node;

            request.setSession(session);
            request.setRoot(obj);

        }

        request.addRequest(node);

    }

    public BaseModelInsertWrapper<E> clearCache(boolean clearCache){
        request.clearCache(clearCache);
        return this;
    }


    public BaseModelInsertWrapper<E> root(Object root){
        request.setRoot(root);
        return this;
    }


    public  BaseModelInsertWrapper<E> field(String name, Object value) {
        wrapper.fields().field(name,value);
        return this;
    }
    public  BaseModelInsertWrapper<E> field(LambdaPropertyFunction<E> name, Object value) {
        wrapper.fields().field(name,value);
        return this;
    }

    public IInsertWrapper getWrapper() {
        return wrapper;
    }


    public ExecuteRequest<?> getRequest() {
        return request;
    }

    public int execute(){
        if(request == null){
            return 0;
        }
        return request.execute();
    }


}

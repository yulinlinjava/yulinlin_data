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

public class BaseModelInsertWrapper<E,R extends BaseModelInsertWrapper<E,R>> {

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

    public R batch(boolean batch){
        request.setBatch(batch);
        return (R)this;
    }

    public R batch(){
        return batch(true);
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

    public R cache(){
        request.setCache(true);
        return (R)this;
    }

    public R cache(boolean cache){
        request.setCache(cache);
        return (R)this;
    }


    public R root(Object root){
        request.setRoot(root);
        return (R)this;
    }


    public  BaseModelInsertWrapper<E,R> field(String name, Object value) {
        wrapper.fields().field(name,value);
        return this;
    }
    public  BaseModelInsertWrapper<E,R> field(LambdaPropertyFunction<E> name, Object value) {
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

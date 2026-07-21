package com.yulinlin.data.core.model;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IInsertWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IDeleteWrapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BaseModelDeleteWrapper<
        E,
        W extends IConditionWrapper<E,W>,
        R extends ModelConditionWrapper<E,W,R>
        >
        extends ModelConditionWrapper<E,
   W,R>  {



    private IDeleteWrapper<E,?,W> wrapper;

    private ExecuteRequest<?> request;


    public BaseModelDeleteWrapper(String session,Object obj) {
        init(session,obj);
    }



    public R batch(boolean batch){
        request.setBatch(batch);
        return (R)this;
    }

    public R batch(){
        return batch(true);
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


    private void initOne(String session, Object obj){
        if(obj == null){
            return;
        }
        if(obj instanceof Class){
            obj  = ReflectionUtil.newInstance((Class) obj);
        }

        IDeleteWrapper node =  SessionUtil.route().getWrapperFactory().createDeleteWrapper(obj);

        if(request == null){
            this.request = ExecuteRequest.ofDelete(obj.getClass());
            this.wrapper = node;

            request.setSession(session);
            request.setRoot(obj);

        }


        request.addRequest(node);

    }


    public R root(Object root){
        request.setRoot(root);
        return (R)this;
    }

    public R cache(){
        request.setCache(true);
        return (R)this;
    }

    public R cache(boolean cache){
        request.setCache(cache);
        return (R)this;
    }

    @Override
    protected Class getModelClass() {
        return request.getEntityClass();
    }


    public ExecuteRequest<?> getRequest() {
        return request;
    }



    public int execute()

    {
        if(request == null){
            return 0;
        }
        return request.execute();
    }


    public IDeleteWrapper<E, ?, W> getWrapper() {
        return wrapper;
    }

    @Override
    public IConditionWrapper<E, W> where() {
        return getWrapper().where();
    }
}

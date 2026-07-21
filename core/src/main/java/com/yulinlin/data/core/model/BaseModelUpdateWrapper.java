package com.yulinlin.data.core.model;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IDeleteWrapper;
import com.yulinlin.data.core.wrapper.IInsertWrapper;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BaseModelUpdateWrapper<E,
        W extends IConditionWrapper<E,W>,
        R extends BaseModelUpdateWrapper<E,W,R>>
        extends ModelConditionWrapper<E,W,R>

{


    private IUpdateWrapper<E,?,W,?> wrapper;

    private ExecuteRequest<?> request;


    public BaseModelUpdateWrapper(String session, Object model) {
        init(session,model);
    }



    public R batch(boolean batch){
        request.setBatch(batch);
        return (R)this;
    }

    public R batch(){
        return batch(true);
    }



    public void init(String session, Object list){
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

        IUpdateWrapper node =  SessionUtil.route().getWrapperFactory().createUpdateWrapper(obj);

        if(request == null){
            this.request = ExecuteRequest.ofUpdate(obj.getClass());
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

    public IUpdateWrapper getWrapper() {
        return wrapper;
    }

    @Override
    protected Class getModelClass() {
        return request.getEntityClass();
    }

    public R field(String fieldName, Object value) {
         wrapper.fields().field(fieldName,value);
        return (R)this;
    }

    public R field(LambdaPropertyFunction<E> name, Object value) {
        wrapper.fields().field(name,value);
        return (R)this;
    }


    public R inc(String fieldName, Number value) {
        wrapper.fields().inc(
                fieldName,value);
        return (R)this;
    }

    public R inc(LambdaPropertyFunction<E> name, Number value) {

        wrapper.fields().inc(
                name,value);
        return (R)this;
    }

    public R dec(String name, Number value) {

        wrapper.fields().dec(name,value);
        return (R)this;
    }

    public R dec(LambdaPropertyFunction<E> name, Number value) {



        wrapper.fields().dec(
                name,value);
        return (R)this;
    }

    @Override
    public IConditionWrapper<E, W> where() {
        return wrapper.where();
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


}

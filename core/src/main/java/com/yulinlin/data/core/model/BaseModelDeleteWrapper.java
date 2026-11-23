package com.yulinlin.data.core.model;

import java.util.Map;
import java.util.function.Consumer;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.anno.ConditionEnum;
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

public class BaseModelDeleteWrapper<E>
        extends ModelConditionWrapper<E>  {



    private IDeleteWrapper<E> wrapper;

    private ExecuteRequest<?> request;


    public BaseModelDeleteWrapper(String session,Object obj) {
        init(session,obj);
    }



    public BaseModelDeleteWrapper<E> batch(boolean batch){
        request.setBatch(batch);
        return this;
    }

    public BaseModelDeleteWrapper<E> batch(){
        return batch(true);
    }

    /** Enqueues this write and returns {@code 0} immediately after successful submission. */
    public BaseModelDeleteWrapper<E> queue(){
        if (request != null) request.queue();
        return this;
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


    public BaseModelDeleteWrapper<E> root(Object root){
        request.setRoot(root);
        return this;
    }

    public BaseModelDeleteWrapper<E> clearCache(boolean clearCache){
        request.clearCache(clearCache);
        return this;
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


    public IDeleteWrapper<E> getWrapper() {
        return wrapper;
    }

    @Override
    public IConditionWrapper<E> where() {
        return getWrapper().where();
    }

    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public BaseModelDeleteWrapper<E> parseWhereCondition(Object query) { super.parseWhereCondition(query); return this; }
    @Override public BaseModelDeleteWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public BaseModelDeleteWrapper<E> struct() { super.struct(); return this; }
    @Override public BaseModelDeleteWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public BaseModelDeleteWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public BaseModelDeleteWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public BaseModelDeleteWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public BaseModelDeleteWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public BaseModelDeleteWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public BaseModelDeleteWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelDeleteWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelDeleteWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public BaseModelDeleteWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public BaseModelDeleteWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public BaseModelDeleteWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public BaseModelDeleteWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public BaseModelDeleteWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelDeleteWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelDeleteWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public BaseModelDeleteWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public BaseModelDeleteWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public BaseModelDeleteWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public BaseModelDeleteWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public BaseModelDeleteWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

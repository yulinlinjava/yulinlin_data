package com.yulinlin.data.core.model;

import java.util.Map;
import java.util.function.Consumer;
import com.yulinlin.data.core.anno.ConditionEnum;
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

public class BaseModelUpdateWrapper<E>
        extends ModelConditionWrapper<E>

{


    private IUpdateWrapper<E> wrapper;

    private ExecuteRequest<?> request;


    public BaseModelUpdateWrapper(String session, Object model) {
        init(session,model);
    }



    public BaseModelUpdateWrapper<E> batch(boolean batch){
        request.setBatch(batch);
        return this;
    }

    public BaseModelUpdateWrapper<E> batch(){
        return batch(true);
    }

    /** Enqueues this write and returns {@code 0} immediately after successful submission. */
    public BaseModelUpdateWrapper<E> queue(){
        if (request != null) request.queue();
        return this;
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



    public BaseModelUpdateWrapper<E> root(Object root){
        request.setRoot(root);
        return this;
    }
    public BaseModelUpdateWrapper<E> clearCache(boolean clearCache){
        request.clearCache(clearCache);
        return this;
    }

    public IUpdateWrapper getWrapper() {
        return wrapper;
    }

    @Override
    protected Class getModelClass() {
        return request.getEntityClass();
    }

    public BaseModelUpdateWrapper<E> field(String fieldName, Object value) {
         wrapper.fields().field(fieldName,value);
        return this;
    }

    public BaseModelUpdateWrapper<E> field(LambdaPropertyFunction<E> name, Object value) {
        wrapper.fields().field(name,value);
        return this;
    }


    public BaseModelUpdateWrapper<E> inc(String fieldName, Number value) {
        wrapper.fields().inc(
                fieldName,value);
        return this;
    }

    public BaseModelUpdateWrapper<E> inc(LambdaPropertyFunction<E> name, Number value) {

        wrapper.fields().inc(
                name,value);
        return this;
    }

    public BaseModelUpdateWrapper<E> dec(String name, Number value) {

        wrapper.fields().dec(name,value);
        return this;
    }

    public BaseModelUpdateWrapper<E> dec(LambdaPropertyFunction<E> name, Number value) {



        wrapper.fields().dec(
                name,value);
        return this;
    }

    @Override
    public IConditionWrapper<E> where() {
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



    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public BaseModelUpdateWrapper<E> parseWhereCondition(Object query) { super.parseWhereCondition(query); return this; }
    @Override public BaseModelUpdateWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public BaseModelUpdateWrapper<E> struct() { super.struct(); return this; }
    @Override public BaseModelUpdateWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public BaseModelUpdateWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public BaseModelUpdateWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public BaseModelUpdateWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public BaseModelUpdateWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public BaseModelUpdateWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public BaseModelUpdateWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelUpdateWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelUpdateWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public BaseModelUpdateWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public BaseModelUpdateWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public BaseModelUpdateWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public BaseModelUpdateWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public BaseModelUpdateWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelUpdateWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelUpdateWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public BaseModelUpdateWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public BaseModelUpdateWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public BaseModelUpdateWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public BaseModelUpdateWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public BaseModelUpdateWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

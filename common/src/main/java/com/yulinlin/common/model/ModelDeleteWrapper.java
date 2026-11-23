package com.yulinlin.common.model;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.model.BaseModelDeleteWrapper;
import com.yulinlin.data.core.model.ModelConditionWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import java.util.Collection;

public class ModelDeleteWrapper<E>
        extends BaseModelDeleteWrapper<E> {

    public ModelDeleteWrapper(String session, Object obj) {
        super(session, obj);
    }





    public static <E>  ModelDeleteWrapper<E> newInstance(E obj){
        return  new ModelDeleteWrapper(null,obj);
    }

    public static <E>  ModelDeleteWrapper<E> newInstance(Class<E> clazz){
        return  new ModelDeleteWrapper(null,clazz);
    }

    public static <E>  ModelDeleteWrapper<E> newInstance( Collection list){
        return  new ModelDeleteWrapper(null,list);
    }



    public static <E>  ModelDeleteWrapper<E>  newInstance(String session, E obj){
        return  new ModelDeleteWrapper(session,obj);
    }

    public static <E>  ModelDeleteWrapper<E>  newInstance(String session, Class<E> clazz){
        return  new ModelDeleteWrapper(session,clazz);
    }

    public static <E>  ModelDeleteWrapper<E>  newInstance(String session, Collection<E> list){
        return  new ModelDeleteWrapper(session,list);
    }



    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public ModelDeleteWrapper<E> batch(boolean batch) { super.batch(batch); return this; }
    @Override public ModelDeleteWrapper<E> batch() { super.batch(); return this; }
    @Override public ModelDeleteWrapper<E> queue() { super.queue(); return this; }
    @Override public ModelDeleteWrapper<E> root(Object root) { super.root(root); return this; }
    @Override public ModelDeleteWrapper<E> clearCache(boolean clearCache) { super.clearCache(clearCache); return this; }
    @Override public ModelDeleteWrapper<E> parseWhereCondition(Object query) { super.parseWhereCondition(query); return this; }
    @Override public ModelDeleteWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public ModelDeleteWrapper<E> struct() { super.struct(); return this; }
    @Override public ModelDeleteWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public ModelDeleteWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public ModelDeleteWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public ModelDeleteWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public ModelDeleteWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public ModelDeleteWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public ModelDeleteWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public ModelDeleteWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public ModelDeleteWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public ModelDeleteWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public ModelDeleteWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public ModelDeleteWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public ModelDeleteWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public ModelDeleteWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public ModelDeleteWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public ModelDeleteWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public ModelDeleteWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public ModelDeleteWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public ModelDeleteWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelDeleteWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelDeleteWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public ModelDeleteWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public ModelDeleteWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public ModelDeleteWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public ModelDeleteWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public ModelDeleteWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public ModelDeleteWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public ModelDeleteWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public ModelDeleteWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelDeleteWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelDeleteWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public ModelDeleteWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public ModelDeleteWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public ModelDeleteWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public ModelDeleteWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public ModelDeleteWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

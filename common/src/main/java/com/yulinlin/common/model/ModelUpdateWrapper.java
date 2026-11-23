package com.yulinlin.common.model;

import java.util.Map;
import java.util.function.Consumer;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.model.BaseModelUpdateWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import java.util.Collection;
import java.util.List;

public class ModelUpdateWrapper<E>
        extends BaseModelUpdateWrapper<E> {

    public ModelUpdateWrapper(String session, Object model) {
        super(session, model);
    }

    public static <E> ModelUpdateWrapper<E> newInstance(E obj){
        ModelUpdateWrapper wrapper = new ModelUpdateWrapper(null,obj);
        return wrapper;
    }


    public static <E> ModelUpdateWrapper<E> newInstance( Class<E> clazz){

        return new ModelUpdateWrapper(null,clazz);
    }


    public static <E> ModelUpdateWrapper<E> newInstance( Collection<E> list){


        return new ModelUpdateWrapper(null,list);
    }







    public static <E> ModelUpdateWrapper<E> newInstance(String session, E obj){
        return new ModelUpdateWrapper(session,obj);
    }

    public static <E> ModelUpdateWrapper<E> newInstance(String session,Collection<E> list){
        return new ModelUpdateWrapper(session,list);
    }



    public static <E> ModelUpdateWrapper<E> newInstance(String session,Class<E> clazz){


        return new ModelUpdateWrapper(session,clazz);
    }


    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public ModelUpdateWrapper<E> batch(boolean batch) { super.batch(batch); return this; }
    @Override public ModelUpdateWrapper<E> batch() { super.batch(); return this; }
    @Override public ModelUpdateWrapper<E> queue() { super.queue(); return this; }
    @Override public ModelUpdateWrapper<E> root(Object root) { super.root(root); return this; }
    @Override public ModelUpdateWrapper<E> clearCache(boolean clearCache) { super.clearCache(clearCache); return this; }
    @Override public ModelUpdateWrapper<E> field(String fieldName, Object value) { super.field(fieldName, value); return this; }
    @Override public ModelUpdateWrapper<E> field(LambdaPropertyFunction<E> name, Object value) { super.field(name, value); return this; }
    @Override public ModelUpdateWrapper<E> inc(String fieldName, Number value) { super.inc(fieldName, value); return this; }
    @Override public ModelUpdateWrapper<E> inc(LambdaPropertyFunction<E> name, Number value) { super.inc(name, value); return this; }
    @Override public ModelUpdateWrapper<E> dec(String name, Number value) { super.dec(name, value); return this; }
    @Override public ModelUpdateWrapper<E> dec(LambdaPropertyFunction<E> name, Number value) { super.dec(name, value); return this; }
    @Override public ModelUpdateWrapper<E> parseWhereCondition(Object query) { super.parseWhereCondition(query); return this; }
    @Override public ModelUpdateWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public ModelUpdateWrapper<E> struct() { super.struct(); return this; }
    @Override public ModelUpdateWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public ModelUpdateWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public ModelUpdateWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public ModelUpdateWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public ModelUpdateWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public ModelUpdateWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public ModelUpdateWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public ModelUpdateWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public ModelUpdateWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public ModelUpdateWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public ModelUpdateWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public ModelUpdateWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public ModelUpdateWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public ModelUpdateWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public ModelUpdateWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public ModelUpdateWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public ModelUpdateWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public ModelUpdateWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public ModelUpdateWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelUpdateWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelUpdateWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public ModelUpdateWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public ModelUpdateWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public ModelUpdateWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public ModelUpdateWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public ModelUpdateWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public ModelUpdateWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public ModelUpdateWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public ModelUpdateWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelUpdateWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelUpdateWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public ModelUpdateWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public ModelUpdateWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public ModelUpdateWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public ModelUpdateWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public ModelUpdateWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

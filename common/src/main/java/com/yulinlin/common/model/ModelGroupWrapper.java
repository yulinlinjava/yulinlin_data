package com.yulinlin.common.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.time.Duration;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.model.BaseModelGroupWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

public class ModelGroupWrapper<E>
        extends BaseModelGroupWrapper<E> {
    public ModelGroupWrapper(String session, Object model) {
        super(session, model);
    }

    public static <E> ModelGroupWrapper<E> newInstance(E query){
        return new ModelGroupWrapper(null,query);
    }

    public static<E> ModelGroupWrapper<E> newInstance(Class<E> clazz){
        return new ModelGroupWrapper(null,clazz);
    }

    public static <E> ModelGroupWrapper<E> newInstance(Class<E> clazz,Object query){
        ModelGroupWrapper<E> objectBaseModelGroupWrapper = newInstance(clazz);
        objectBaseModelGroupWrapper.parseWhereCondition(query);
        return objectBaseModelGroupWrapper;
    }






    public static<E> ModelGroupWrapper<E> newInstance(String session, E obj){
        ModelGroupWrapper wrapper = new ModelGroupWrapper(session,obj);
        return wrapper;
    }

    public static <E> ModelGroupWrapper<E> newInstance(String session, Class<E> clazz){
        ModelGroupWrapper wrapper = new ModelGroupWrapper(session,clazz);
        return wrapper;
    }




    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public ModelGroupWrapper<E> useIndex(String... indexNames) { super.useIndex(indexNames); return this; }
    @Override public ModelGroupWrapper<E> apply(Consumer<IGroupWrapper<E>> func) { super.apply(func); return this; }
    @Override public ModelGroupWrapper<E> root(Object root) { super.root(root); return this; }
    @Override public ModelGroupWrapper<E> having(Consumer<IConditionWrapper<E>> f) { super.having(f); return this; }
    @Override public ModelGroupWrapper<E> cache() { super.cache(); return this; }
    @Override public ModelGroupWrapper<E> cache(Duration ttl) { super.cache(ttl); return this; }
    @Override public ModelGroupWrapper<E> cache(CacheMode mode) { super.cache(mode); return this; }
    @Override public ModelGroupWrapper<E> cache(CacheMode mode, Duration ttl) { super.cache(mode, ttl); return this; }
    @Override public ModelGroupWrapper<E> cache(boolean cache) { super.cache(cache); return this; }
    @Override public ModelGroupWrapper<E> cacheNamespaces(String... resources) { super.cacheNamespaces(resources); return this; }
    @Override public ModelGroupWrapper<E> page(int pageNumber, int pageSize) { super.page(pageNumber, pageSize); return this; }
    @Override public ModelGroupWrapper<E> orderByAsc(String name) { super.orderByAsc(name); return this; }
    @Override public ModelGroupWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) { super.orderBy(name, asc); return this; }
    @Override public ModelGroupWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) { super.orderByAsc(name); return this; }
    @Override public ModelGroupWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) { super.orderByDesc(name); return this; }
    @Override public ModelGroupWrapper<E> orderByDesc(String name) { super.orderByDesc(name); return this; }
    @Override public ModelGroupWrapper<E> parseWhereCondition(Object query) { super.parseWhereCondition(query); return this; }
    @Override public ModelGroupWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public ModelGroupWrapper<E> struct() { super.struct(); return this; }
    @Override public ModelGroupWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public ModelGroupWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public ModelGroupWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public ModelGroupWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public ModelGroupWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public ModelGroupWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public ModelGroupWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public ModelGroupWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public ModelGroupWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public ModelGroupWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public ModelGroupWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public ModelGroupWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public ModelGroupWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public ModelGroupWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public ModelGroupWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public ModelGroupWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public ModelGroupWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public ModelGroupWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public ModelGroupWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelGroupWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelGroupWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public ModelGroupWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public ModelGroupWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public ModelGroupWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public ModelGroupWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public ModelGroupWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public ModelGroupWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public ModelGroupWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public ModelGroupWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelGroupWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelGroupWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public ModelGroupWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public ModelGroupWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public ModelGroupWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public ModelGroupWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public ModelGroupWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

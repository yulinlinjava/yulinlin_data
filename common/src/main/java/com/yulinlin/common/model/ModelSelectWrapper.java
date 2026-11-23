package com.yulinlin.common.model;

import java.util.Collection;
import java.util.Map;
import java.time.Duration;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.wrapper.ISelectWrapper;
import com.yulinlin.data.core.wrapper.IAsFieldListWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.common.domain.ITreeNode;
import com.yulinlin.common.util.TreeUtil;
import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import java.util.List;
import java.util.function.Consumer;

public class ModelSelectWrapper<E>
        extends BaseModelSelectWrapper<E> {

    public ModelSelectWrapper(String session, Object model) {
        super(session, model);
    }

    public  List<E> selectTree() {
        if(ITreeNode.class.isAssignableFrom( getModelClass())){
            List<ITreeNode> list = (   List<ITreeNode> ) super.selectList();
            return ( List<E>)TreeUtil.buildTree(list);
        }
        throw new RuntimeException("必须实现树接口");

    }
    public  boolean exist() {
        page(1,1);
        return count() > 0;
    }

    public static <E> ModelSelectWrapper<E> newInstance(E obj){
        ModelSelectWrapper<E> wrapper = new ModelSelectWrapper(null,obj);
        return wrapper;
    }

    public static <E> ModelSelectWrapper<E>newInstance(Class<E> eClass){
        ModelSelectWrapper<E> wrapper = new ModelSelectWrapper(null,eClass);
        return wrapper;
    }
    public static <E> ModelSelectWrapper<E>newInstance(Class<E> eClass,Object query){
        E e = ReflectionUtil.newInstance(eClass);
        ModelSelectWrapper wrapper =  newInstance(e);
        wrapper.parseWhereCondition(query);
        return wrapper;
    }

    public static <E> ModelSelectWrapper<E>newInstance(String session, Object obj){

        ModelSelectWrapper<E> wrapper = new ModelSelectWrapper(session,obj);
        return wrapper;
    }

    public static <E> ModelSelectWrapper<E>newInstance(String session,Class<E> clazz){

        ModelSelectWrapper<E> wrapper = new ModelSelectWrapper(session,clazz);
        return wrapper;
    }

    public static <E> ModelSelectWrapper<E>newInstance(String session,Class<E> clazz,Object query){
        ModelSelectWrapper wrapper =    newInstance(session,clazz);
        wrapper.parseWhereCondition(query);
        return    wrapper;
    }




    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public ModelSelectWrapper<E> fieldMeta(Consumer<IAsFieldListWrapper<E>> consumer) { super.fieldMeta(consumer); return this; }
    @Override public ModelSelectWrapper<E> useIndex(String... indexNames) { super.useIndex(indexNames); return this; }
    @Override public ModelSelectWrapper<E> apply(Consumer<ISelectWrapper<E>> func) { super.apply(func); return this; }
    @Override public ModelSelectWrapper<E> root(Object root) { super.root(root); return this; }
    @Override public ModelSelectWrapper<E> page(int pageNumber, int pageSize) { super.page(pageNumber, pageSize); return this; }
    @Override public ModelSelectWrapper<E> highlight(String name) { super.highlight(name); return this; }
    @Override public ModelSelectWrapper<E> highlight(LambdaPropertyFunction<E> name) { super.highlight(name); return this; }
    @Override public ModelSelectWrapper<E> orderBy(String name, boolean asc) { super.orderBy(name, asc); return this; }
    @Override public ModelSelectWrapper<E> cache() { super.cache(); return this; }
    @Override public ModelSelectWrapper<E> cache(Duration ttl) { super.cache(ttl); return this; }
    @Override public ModelSelectWrapper<E> cache(CacheMode mode) { super.cache(mode); return this; }
    @Override public ModelSelectWrapper<E> cache(CacheMode mode, Duration ttl) { super.cache(mode, ttl); return this; }
    @Override public ModelSelectWrapper<E> cache(boolean cache) { super.cache(cache); return this; }
    @Override public ModelSelectWrapper<E> cacheNamespaces(String... resources) { super.cacheNamespaces(resources); return this; }
    @Override public ModelSelectWrapper<E> autoUpdate() { super.autoUpdate(); return this; }
    @Override public ModelSelectWrapper<E> autoUpdate(boolean enabled) { super.autoUpdate(enabled); return this; }
    @Override public ModelSelectWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) { super.orderBy(name, asc); return this; }
    @Override public ModelSelectWrapper<E> orderByAsc(String name) { super.orderByAsc(name); return this; }
    @Override public ModelSelectWrapper<E> orderBy(Consumer<BaseModelSelectWrapper<E>> func) { super.orderBy(func); return this; }
    @Override public ModelSelectWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) { super.orderByAsc(name); return this; }
    @Override public ModelSelectWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) { super.orderByDesc(name); return this; }
    @Override public ModelSelectWrapper<E> orderByDesc(String name) { super.orderByDesc(name); return this; }
    @Override public ModelSelectWrapper<E> pageByPrimaryKey(Object idValue, int pageSize) { super.pageByPrimaryKey(idValue, pageSize); return this; }
    @Override public ModelSelectWrapper<E> parseWhereCondition(Object query) { super.parseWhereCondition(query); return this; }
    @Override public ModelSelectWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public ModelSelectWrapper<E> struct() { super.struct(); return this; }
    @Override public ModelSelectWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public ModelSelectWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public ModelSelectWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public ModelSelectWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public ModelSelectWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public ModelSelectWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public ModelSelectWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public ModelSelectWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public ModelSelectWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public ModelSelectWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public ModelSelectWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public ModelSelectWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public ModelSelectWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public ModelSelectWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public ModelSelectWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public ModelSelectWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public ModelSelectWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public ModelSelectWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public ModelSelectWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelSelectWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public ModelSelectWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public ModelSelectWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public ModelSelectWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public ModelSelectWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public ModelSelectWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public ModelSelectWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public ModelSelectWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public ModelSelectWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public ModelSelectWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelSelectWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public ModelSelectWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public ModelSelectWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public ModelSelectWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public ModelSelectWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public ModelSelectWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public ModelSelectWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

package com.yulinlin.data.core.model;

import java.util.Collection;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.Page;
import lombok.SneakyThrows;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BaseModelGroupWrapper<E>

extends ModelConditionWrapper<E>

{


    private IGroupWrapper<E> wrapper;

    private QueryRequest<E> request;



    public BaseModelGroupWrapper(String session, Object model) {
        init(session, model);
    }


    /** Per-query index candidates: applied by H2; MySQL, PostgreSQL and other built-in backends ignore valid hints. */
    public BaseModelGroupWrapper<E> useIndex(String... indexNames) {
        request.useIndex(indexNames);
        return this;
    }

    public BaseModelGroupWrapper<E> apply(Consumer<IGroupWrapper<E>>  func){
        func.accept(wrapper);
        return this;
    }

    /** Returns native execution-plan rows without fetching entities or using the query cache. */
    public List<Map<String, Object>> explain() { return request.explain(); }

    /** Returns the plan that counts groups, retaining GROUP BY/HAVING but not pagination. */
    public List<Map<String, Object>> explainCount() { return request.explainCount(); }

    private void init(String session, Object model){
        if (model instanceof Class){
            model = ReflectionUtil.newInstance((Class) model);
        }

        this.wrapper =   SessionUtil.route().getWrapperFactory().createGroupWrapper(model);
        this.request =
                QueryRequest.newInstance((Class<E>)model.getClass(),wrapper);
        request.setSession(session);
        request.setRoot(model);
    }


    public BaseModelGroupWrapper<E> root(Object root){
        request.setRoot(root);
        return this;
    }

    public IGroupWrapper getWrapper() {
        return wrapper;
    }

    @Override
    protected Class getModelClass() {
        return request.getEntityClass();
    }

    @SneakyThrows
    public E selectOne(){
        return request.selectOne();

    }
    @SneakyThrows
    public List<E> selectList(){
        return request.selectList();
    }

    @SneakyThrows
    public Page<E> selectPage(){
        return request.selectPage();
    }

    @SneakyThrows
    public long count(){
        return request.count();

    }

    @SneakyThrows
    public <K> Map<K,E> selectByMap(String key){
        return (Map<K,E>)request.selectByMap(key);
    }


    @SneakyThrows
    public <K> Map<K,List<E>> selectByGroup(String key){
        return (Map<K,List<E>>)request.selectByGroup(key);
    }

    public BaseModelGroupWrapper<E> having(Consumer<IConditionWrapper<E>> f) {
        f.accept(having());
        return this;
    }

    public IConditionWrapper<E> having() {
        return wrapper.having();
    }

    @Override
    public IConditionWrapper<E> where() {

        return wrapper.where();
    }

    public BaseModelGroupWrapper<E> cache(){
        request.cache();
        return this;
    }

    public BaseModelGroupWrapper<E> cache(Duration ttl){
        request.cache(ttl);
        return this;
    }

    public BaseModelGroupWrapper<E> cache(CacheMode mode){
        request.cache(mode);
        return this;
    }

    public BaseModelGroupWrapper<E> cache(CacheMode mode, Duration ttl){
        request.cache(mode, ttl);
        return this;
    }

    public BaseModelGroupWrapper<E> cache(boolean cache){
        request.cache(cache);
        return this;
    }

    public BaseModelGroupWrapper<E> cacheNamespaces(String... resources) {
        request.cacheNamespaces(resources);
        return this;
    }

    /**
     * 自定义分页
     * @param pageNumber
     * @param pageSize
     * @return
     */
    public BaseModelGroupWrapper<E> page(int pageNumber, int pageSize){
        wrapper.page(pageNumber,pageSize);
        return this;
    }

    public BaseModelGroupWrapper<E> orderByAsc(String name){
        wrapper.orderByAsc(name);
        return this;
    }

    public  BaseModelGroupWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        wrapper.orderBy(name,asc);
        return this;
    }

    public  BaseModelGroupWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) {
        wrapper.orderByAsc(name);
        return this;
    }
    public BaseModelGroupWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) {
        wrapper.orderByDesc(name);
        return this;
    }

    public BaseModelGroupWrapper<E> orderByDesc(String name){
        wrapper.orderByDesc(name);
        return this;
    }


    public QueryRequest<E> getRequest() {
        return request;
    }

    @Override
    public BaseModelGroupWrapper<E> parseWhereCondition(Object query) {
        super.parseWhereCondition(query);
        return this;
    }



    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public BaseModelGroupWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public BaseModelGroupWrapper<E> struct() { super.struct(); return this; }
    @Override public BaseModelGroupWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public BaseModelGroupWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public BaseModelGroupWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public BaseModelGroupWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public BaseModelGroupWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public BaseModelGroupWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public BaseModelGroupWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelGroupWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelGroupWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public BaseModelGroupWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public BaseModelGroupWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public BaseModelGroupWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public BaseModelGroupWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public BaseModelGroupWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelGroupWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelGroupWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public BaseModelGroupWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public BaseModelGroupWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public BaseModelGroupWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public BaseModelGroupWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public BaseModelGroupWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

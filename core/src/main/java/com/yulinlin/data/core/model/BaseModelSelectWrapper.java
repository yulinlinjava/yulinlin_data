package com.yulinlin.data.core.model;

import java.util.Collection;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IAsFieldListWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.core.wrapper.ISelectWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.Page;
import lombok.SneakyThrows;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BaseModelSelectWrapper<E>
        
        extends ModelConditionWrapper<E> {



    private ISelectWrapper<E> wrapper;

    private QueryRequest<E> request;



    public BaseModelSelectWrapper(String session, Object model) {
        init(session,model);
    }


    /** Per-query index candidates: applied by H2; MySQL, PostgreSQL and other built-in backends ignore valid hints. */
    public BaseModelSelectWrapper<E> useIndex(String... indexNames) {
        request.useIndex(indexNames);
        return this;
    }

    public BaseModelSelectWrapper<E> apply(Consumer<ISelectWrapper<E>>  func){
        func.accept(wrapper);
        return this;
    }

    /** Returns native execution-plan rows without fetching entities or using the query cache. */
    public List<Map<String, Object>> explain() { return request.explain(); }

    /** Returns the plan of the derived count, without pagination or ordering. */
    public List<Map<String, Object>> explainCount() { return request.explainCount(); }

    private void init(String session, Object model){
        if (model instanceof Class){
            model = ReflectionUtil.newInstance((Class) model);
        }

        this.wrapper =   SessionUtil.route().getWrapperFactory().createSelectWrapper(model);
        this.request =
                QueryRequest.newInstance((Class<E>)model.getClass(),wrapper);
        request.setSession(session);
        request.setRoot(model);
    }

    public BaseModelSelectWrapper<E> root(Object root){
        request.setRoot(root);
        return this;
    }

    public ISelectWrapper getWrapper() {
        return wrapper;
    }

    @Override
    protected Class getModelClass() {
        return request.getEntityClass();
    }

    @SneakyThrows
    public E selectOne(){
        page(1,1);
        return request.selectOne();

    }
    @SneakyThrows
    public List<E> selectList(){
        return  request.selectList();
    }

    /** Reads plain entities one at a time; does not create proxies or load associations. */
    public long selectEach(Consumer<? super E> consumer) {
        return request.selectEach(consumer);
    }

    public Page<E> selectPage(int pageNumber,int pageSize){
        page(pageNumber,pageSize);
        return selectPage();
    }

    @SneakyThrows
    public Page<E> selectPage(){
       return request.selectPage();
    }

    @SneakyThrows
    public int count(){
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

    @Override
    public IConditionWrapper<E> where() {
        return wrapper.where();
    }

    /**
     * 自定义分页
     * @param pageNumber
     * @param pageSize
     * @return
     */
    public BaseModelSelectWrapper<E> page(int pageNumber, int pageSize){
        wrapper.page(pageNumber,pageSize);
        return this;
    }

    public BaseModelSelectWrapper<E> fieldMeta(Consumer<IAsFieldListWrapper<E>> consumer){
        IAsFieldListWrapper<E> fields = wrapper.fields();
        consumer.accept(fields);
        return this;
    }

    public BaseModelSelectWrapper<E> highlight(String name) {
        wrapper.highlight(name);
        return this;
    }

    public BaseModelSelectWrapper<E> highlight(LambdaPropertyFunction<E> name) {
        wrapper.highlight(name);
        return this;
    }

    public  BaseModelSelectWrapper<E> orderBy(String name, boolean asc) {
        wrapper.orderBy(name,asc);
        return this;
    }

    public BaseModelSelectWrapper<E> cache(){
        request.cache();
        return this;
    }

    public BaseModelSelectWrapper<E> cache(Duration ttl){
        request.cache(ttl);
        return this;
    }

    public BaseModelSelectWrapper<E> cache(CacheMode mode){
        request.cache(mode);
        return this;
    }

    public BaseModelSelectWrapper<E> cache(CacheMode mode, Duration ttl){
        request.cache(mode, ttl);
        return this;
    }

    public BaseModelSelectWrapper<E> cache(boolean cache){
        request.cache(cache);
        return this;
    }

    public BaseModelSelectWrapper<E> cacheNamespaces(String... resources) {
        request.cacheNamespaces(resources);
        return this;
    }

    /** Makes this query return transaction-managed entities. */
    public BaseModelSelectWrapper<E> autoUpdate() {
        request.autoUpdate();
        return this;
    }

    /** Overrides the global/@JoinSync automatic-update policy for this query. */
    public BaseModelSelectWrapper<E> autoUpdate(boolean enabled) {
        request.autoUpdate(enabled);
        return this;
    }

    public  BaseModelSelectWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        wrapper.orderBy(name,asc);
        return this;
    }

    public BaseModelSelectWrapper<E> orderByAsc(String name){
        wrapper.orderByAsc(name);
        return this;
    }


    public  BaseModelSelectWrapper<E> orderBy(Consumer<BaseModelSelectWrapper<E>> func) {
        func.accept(this);
        return this;
    }

    public  BaseModelSelectWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) {
        wrapper.orderByAsc(name);
        return this;
    }
    public BaseModelSelectWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) {
        wrapper.orderByDesc(name);
        return this;
    }

    public BaseModelSelectWrapper<E> orderByDesc(String name){
        wrapper.orderByDesc(name);
        return this;
    }


    /**
     * 根据主键分页
     * @param idValue
     * @param pageSize
     * @return
     */
    public BaseModelSelectWrapper<E> pageByPrimaryKey(Object idValue, int pageSize){
         String name =    primaryKeyName();
        IConditionWrapper where =(IConditionWrapper) wrapper.where();
        where.gt(name,idValue);
        wrapper.page(1,pageSize);
        return this;
    }


    public QueryRequest<E> getRequest() {
        return request;
    }

    @Override
    public BaseModelSelectWrapper<E> parseWhereCondition(Object query) {
        super.parseWhereCondition(query);
        return this;
    }


    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public BaseModelSelectWrapper<E> not(Consumer<IConditionWrapper<E>> func) { super.not(func); return this; }
    @Override public BaseModelSelectWrapper<E> struct() { super.struct(); return this; }
    @Override public BaseModelSelectWrapper<E> and(IConditionWrapper<?> func) { super.and(func); return this; }
    @Override public BaseModelSelectWrapper<E> and(Consumer<IConditionWrapper<E>> func) { super.and(func); return this; }
    @Override public BaseModelSelectWrapper<E> condition(String name, ConditionEnum condition, Object value) { super.condition(name, condition, value); return this; }
    @Override public BaseModelSelectWrapper<E> expression(String name,Map<String,Object> data) { super.expression(name, data); return this; }
    @Override public BaseModelSelectWrapper<E> expression(String name) { super.expression(name); return this; }
    @Override public BaseModelSelectWrapper<E> eq(String name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) { super.eq(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> ne(String name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) { super.ne(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> gt(String name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) { super.gt(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> gte(String name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) { super.gte(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> lt(String name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) { super.lt(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> lte(String name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) { super.lte(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> between(String name, Object x, Object y) { super.between(name, x, y); return this; }
    @Override public BaseModelSelectWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelSelectWrapper<E> between(String name, List<?> list) { super.between(name, list); return this; }
    @Override public BaseModelSelectWrapper<E> like(String name, String value) { super.like(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> like(LambdaPropertyFunction<E> name, String list) { super.like(name, list); return this; }
    @Override public BaseModelSelectWrapper<E> match(String name, String value) { super.match(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> match(LambdaPropertyFunction<E> name, String value) { super.match(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> likeRight(String name, String value) { super.likeRight(name, value); return this; }
    @Override public BaseModelSelectWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list) { super.likeRight(name, list); return this; }
    @Override public BaseModelSelectWrapper<E> isNull(String name) { super.isNull(name); return this; }
    @Override public BaseModelSelectWrapper<E> isNull(LambdaPropertyFunction<E> name) { super.isNull(name); return this; }
    @Override public BaseModelSelectWrapper<E> in(String name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelSelectWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) { super.in(name, coll); return this; }
    @Override public BaseModelSelectWrapper<E> nin(String name, Collection<?> coll) { super.nin(name, coll); return this; }
    @Override public BaseModelSelectWrapper<E> eq(Object value) { super.eq(value); return this; }
    @Override public BaseModelSelectWrapper<E> nin(Collection<?> coll) { super.nin(coll); return this; }
    @Override public BaseModelSelectWrapper<E> in(Collection<?> coll) { super.in(coll); return this; }
    @Override public BaseModelSelectWrapper<E> or(Consumer<IConditionWrapper<E>> func) { super.or(func); return this; }
    @Override public BaseModelSelectWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
}

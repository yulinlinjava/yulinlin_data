package com.yulinlin.data.core.model;

import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.Page;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BaseModelGroupWrapper<
        E,
        W extends IConditionWrapper<E,W>,
        R extends BaseModelGroupWrapper<E,W,R>

        >

extends ModelConditionWrapper<E,W,R>

{


    private IGroupWrapper<E,?,W,?,?> wrapper;

    private QueryRequest<E> request;



    public BaseModelGroupWrapper(String session, Object model) {
        init(session, model);
    }


    public R apply(Consumer<IGroupWrapper<E,?,W,?,?>>  func){
        func.accept(wrapper);
        return (R)this;
    }

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


    public R root(Object root){
        request.setRoot(root);
        return (R)this;
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

    public <W extends IConditionWrapper<E, W>> R having(Consumer<IConditionWrapper<E,W>> f) {
        f.accept(having());
        return (R)this;
    }

    public <W extends IConditionWrapper<E,W>> IConditionWrapper<E,W>  having() {
        return (W)wrapper.having();
    }

    @Override
    public IConditionWrapper<E, W> where() {

        return wrapper.where();
    }

    public R cache(){
        request.setCache(true);
        return (R)this;
    }

    public R cache(boolean cache){
        request.setCache(cache);
        return (R)this;
    }

    /**
     * 自定义分页
     * @param pageNumber
     * @param pageSize
     * @return
     */
    public R page(int pageNumber, int pageSize){
        wrapper.page(pageNumber,pageSize);
        return (R)this;
    }

    public R orderByAsc(String name){
        wrapper.orderByAsc(name);
        return (R)this;
    }

    public  R orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        wrapper.orderBy(name,asc);
        return (R)this;
    }

    public  R orderByAsc(LambdaPropertyFunction<E> name) {
        wrapper.orderByAsc(name);
        return (R)this;
    }
    public R orderByDesc(LambdaPropertyFunction<E> name) {
        wrapper.orderByDesc(name);
        return (R)this;
    }

    public R orderByDesc(String name){
        wrapper.orderByDesc(name);
        return (R)this;
    }


    public QueryRequest<E> getRequest() {
        return request;
    }

    @Override
    public R parseWhereCondition(Object query) {
        return super.parseWhereCondition(query);
    }


}

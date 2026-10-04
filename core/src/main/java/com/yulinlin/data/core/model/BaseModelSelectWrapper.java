package com.yulinlin.data.core.model;

import com.yulinlin.data.core.request.QueryRequest;
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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BaseModelSelectWrapper<E,

        W extends IConditionWrapper<E,W>,
        R extends BaseModelSelectWrapper<E,W,R>>
        
        extends ModelConditionWrapper<E,W,R> {



    private ISelectWrapper<E,?,W,?> wrapper;

    private QueryRequest<E> request;



    public BaseModelSelectWrapper(String session, Object model) {
        init(session,model);
    }


    public R apply(Consumer<ISelectWrapper<E,?,W,?>>  func){
        func.accept(wrapper);
        return (R)this;
    }

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

    public R root(Object root){
        request.setRoot(root);
        return (R)this;
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
    public IConditionWrapper<E, W> where() {
        return wrapper.where();
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

    public <A extends IAsFieldListWrapper<E,A>>  R fieldMeta(Consumer<A> consumer){
        A fields =(A) wrapper.fields();
        consumer.accept(fields);
        return (R)this;
    }

    public  R orderBy(String name, boolean asc) {
        wrapper.orderBy(name,asc);
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

    public  R orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        wrapper.orderBy(name,asc);
        return (R)this;
    }

    public R orderByAsc(String name){
        wrapper.orderByAsc(name);
        return (R)this;
    }


    public  R orderBy(Consumer<R> func) {
        func.accept((R)this);
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


    /**
     * 根据主键分页
     * @param idValue
     * @param pageSize
     * @return
     */
    public R pageByPrimaryKey(Object idValue, int pageSize){
         String name =    primaryKeyName();
        IConditionWrapper where =(IConditionWrapper) wrapper.where();
        where.gt(name,idValue);
        wrapper.page(1,pageSize);
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

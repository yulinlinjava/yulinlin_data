package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.From;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.node.order.Order;
import com.yulinlin.data.core.util.LambdaPropertyUtil;
import com.yulinlin.data.core.wrapper.IAsFieldListWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.ISelectWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractSelectWrapper<E>

        extends AbstractWrapper<ISelectWrapper<E>> implements ISelectWrapper<E> {

    protected From from;

    private boolean  isLock = false;

    
    private  IConditionWrapper<E> where;

    private Order order;

    private int pageNumber = -1;

    private int pageSize = 30;

    
    private IAsFieldListWrapper<E> selects;

    

    public AbstractSelectWrapper(IConditionWrapper<E> where) {
        this.where =where;
        this.selects = buildSelects();
        this.order =new Order();

    }

    protected  abstract IAsFieldListWrapper<E> buildSelects();

    

    public AbstractSelectWrapper<E> table(String name){
        this.from = new Store(name);
        return this;
    }
    public AbstractSelectWrapper<E>  table(String name, String alias){
        this.from = new Store(name,alias);
        return this;
    }

    public AbstractSelectWrapper<E> lock() {
        this. isLock = true;
        return this;
    }

    public boolean isLock() {
        return isLock;
    }

    public From getFrom() {
        return from;
    }

    @Override
    public IAsFieldListWrapper<E> fields() {
        return selects;
    }

    @Override
    public AbstractSelectWrapper<E> fields(Consumer<IAsFieldListWrapper<E>> consumer) {
        consumer.accept(selects);
        return this;
    }

    @Override
    public AbstractSelectWrapper<E> highlight(String name) {
        selects.highlight(name);
        return this;
    }

    @Override
    public AbstractSelectWrapper<E> highlight(LambdaPropertyFunction<E> name) {
        selects.highlight(name);
        return this;
    }

    @Override
    public AbstractSelectWrapper<E> orderBy(String name, boolean asc) {
        order.pushOrder(name,asc);
        return this;
    }

    @Override
    public AbstractSelectWrapper<E> page(int pageNumber, int pageSize) {
        if(pageNumber > 0&& pageSize > 0){
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
        }

        return this;
    }

    @Override
    public IConditionWrapper<E>  where() {
        return where;
    }

    @Override
    public AbstractSelectWrapper<E> where(IConditionWrapper<E> wrapper) {
        where.and(wrapper);
        return this;
    }

    @Override
    public AbstractSelectWrapper<E> where(Consumer<IConditionWrapper<E>> func) {
        func.accept(where);
        return this;
    }

    public Order getOrder() {
        return order;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    private String getName(LambdaPropertyFunction name){
        String  fieldName =  LambdaPropertyUtil.getColumnName(name);
        return fieldName;
    }

    @Override
    public AbstractSelectWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        return orderBy(getName(name),asc);
    }

    @Override
    public AbstractSelectWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) {
        return orderBy(name,false);
    }

    @Override
    public AbstractSelectWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) {
        return orderBy(name,true);
    }

    //支持克隆
    @Override
    public AbstractSelectWrapper<E> clone()  {
        try {
            return (AbstractSelectWrapper)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);

        }

    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractSelectWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractSelectWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public AbstractSelectWrapper<E> orderByAsc(String name) { orderBy(name, true); return this; }
    @Override public AbstractSelectWrapper<E> orderByDesc(String name) { orderBy(name, false); return this; }
}

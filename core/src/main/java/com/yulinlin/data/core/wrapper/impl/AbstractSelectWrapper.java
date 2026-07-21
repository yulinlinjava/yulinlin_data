package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.From;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.node.order.Order;
import com.yulinlin.data.core.util.LambdaPropertyUtil;
import com.yulinlin.data.core.wrapper.IAsFieldListWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.ISelectWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

import java.util.function.Consumer;

public abstract class AbstractSelectWrapper<
        E,
        R extends ISelectWrapper<E,R,W,C>,
        W extends IConditionWrapper<E,W>,
        C extends IAsFieldListWrapper<E,C>


        >

        extends AbstractWrapper<R> implements ISelectWrapper<E,R,W,C> {

    protected From from;

    private boolean  isLock = false;

    
    private  W where;


    private Order order;

    private int pageNumber = -1;

    private int pageSize = 30;

    
    private C selects;


    

    public AbstractSelectWrapper(W where) {
        this.where =where;
        this.selects = buildSelects();
        this.order =new Order();

    }



    protected  abstract C buildSelects();

    

    public R table(String name){
        this.from = new Store(name);
        return (R)this;
    }
    public R  table(String name, String alias){
        this.from = new Store(name,alias);
        return (R)this;
    }



    public R lock() {
        this. isLock = true;
        return (R)this;
    }

    public boolean isLock() {
        return isLock;
    }


    public From getFrom() {
        return from;
    }


    @Override
    public C fields() {
        return selects;
    }

    @Override
    public R fields(Consumer<C> consumer) {
        consumer.accept(selects);
        return (R)this;
    }



    @Override
    public R orderBy(String name, boolean asc) {
        order.pushOrder(name,asc);
        return (R)this;
    }

    @Override
    public R page(int pageNumber, int pageSize) {
        if(pageNumber > 0&& pageSize > 0){
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
        }

        return (R)this;
    }


    @Override
    public W  where() {
        return where;
    }

    @Override
    public R where(W wrapper) {
        wrapper.and(wrapper);
        return (R)this;
    }

    @Override
    public R where(Consumer<W> func) {
        func.accept(where);
        return (R)this;
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
    public R orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        return orderBy(getName(name),asc);
    }



    @Override
    public R orderByDesc(LambdaPropertyFunction<E> name) {
        return orderBy(name,false);
    }

    @Override
    public R orderByAsc(LambdaPropertyFunction<E> name) {
        return orderBy(name,true);
    }

    //支持克隆
    @Override
    public AbstractSelectWrapper clone()  {
        try {
            return (AbstractSelectWrapper)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);

        }


    }


}

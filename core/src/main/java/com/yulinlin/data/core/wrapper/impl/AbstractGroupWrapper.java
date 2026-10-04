package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.From;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.node.order.Order;
import com.yulinlin.data.core.util.LambdaPropertyUtil;
import com.yulinlin.data.core.wrapper.IAggregationsWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.core.wrapper.IMetricsWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

import java.util.function.Consumer;

public abstract class AbstractGroupWrapper
        <
                E,
                R extends IGroupWrapper<E,R,W,M,A>,
                W extends IConditionWrapper<E,W>,
                M extends IMetricsWrapper<E,M>,
                A extends IAggregationsWrapper<E,A>
                >

        extends AbstractWrapper<R> implements IGroupWrapper<E,R,W,M,A>{

    protected From from;


    private   W where;


    private Order order;

    private int pageNumber = -1;

    private int pageSize = 30;



    private M metrics;

    private A aggregations;



    private  W having;


    public AbstractGroupWrapper() {
        this.where = buildWhere();
        this.having = buildWhere();
        this.aggregations = buildAggregations();
        this.metrics =buildMetrics();

        this.order =new Order();
    }


    protected abstract W buildWhere();

    protected abstract A buildAggregations();

    protected abstract M buildMetrics();

    @Override
    public R having(W wrapper) {
        having.and(wrapper);
        return (R)this;
    }


    @Override
    public R where(Consumer<W> func) {
        func.accept(where);
        return (R)this;
    }

    public   W having(){
        return having;
    }

    @Override
    public R having(Consumer<W> func) {
        func.accept(having);
        return (R)this;
    }


    public  R table(String name){
        this.from = new Store(name);
        return (R)this;
    }
    public  R table(String name, String alias){
        this.from = new Store(name,alias);
        return (R)this;
    }




    public From getFrom() {
        return from;
    }


    @Override
    public M metrics() {
        return metrics;
    }

    @Override
    public A aggregations() {
        return aggregations;
    }


    @Override
    public W where() {
        return where;
    }


    @Override
    public R where(W wrapper) {
        this.where = wrapper;
        return (R)this;
    }

    @Override
    public R orderBy(String name, boolean asc) {
        order.pushOrder(name,asc);
        return (R)this;
    }

    @Override
    public R page(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        return (R)this;
    }



    public W getWhere() {
        return where;
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
    public  R orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        return orderBy(getName(name),asc);
    }

    @Override
    public  R orderByDesc(LambdaPropertyFunction<E> name) {
        return orderBy(name,false);
    }

    @Override
    public  R orderByAsc(LambdaPropertyFunction<E> name) {
        return orderBy(name,true);
    }

    //支持克隆
    @Override
    public R clone()  {
        try {
            return (R)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);

        }


    }
}

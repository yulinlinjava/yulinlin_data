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
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractGroupWrapper<E>

        extends AbstractWrapper<IGroupWrapper<E>> implements IGroupWrapper<E>{

    protected From from;

    private   IConditionWrapper<E> where;

    private Order order;

    private int pageNumber = -1;

    private int pageSize = 30;

    private IMetricsWrapper<E> metrics;

    private IAggregationsWrapper<E> aggregations;

    private  IConditionWrapper<E> having;

    public AbstractGroupWrapper() {
        this.where = buildWhere();
        this.having = buildWhere();
        this.aggregations = buildAggregations();
        this.metrics =buildMetrics();

        this.order =new Order();
    }

    protected abstract IConditionWrapper<E> buildWhere();

    protected abstract IAggregationsWrapper<E> buildAggregations();

    protected abstract IMetricsWrapper<E> buildMetrics();

    @Override
    public AbstractGroupWrapper<E> having(IConditionWrapper<E> wrapper) {
        having.and(wrapper);
        return this;
    }

    @Override
    public AbstractGroupWrapper<E> where(Consumer<IConditionWrapper<E>> func) {
        func.accept(where);
        return this;
    }

    public   IConditionWrapper<E> having(){
        return having;
    }

    @Override
    public AbstractGroupWrapper<E> having(Consumer<IConditionWrapper<E>> func) {
        func.accept(having);
        return this;
    }

    public  AbstractGroupWrapper<E> table(String name){
        this.from = new Store(name);
        return this;
    }
    public  AbstractGroupWrapper<E> table(String name, String alias){
        this.from = new Store(name,alias);
        return this;
    }

    public From getFrom() {
        return from;
    }

    @Override
    public IMetricsWrapper<E> metrics() {
        return metrics;
    }

    @Override
    public IAggregationsWrapper<E> aggregations() {
        return aggregations;
    }

    @Override
    public IConditionWrapper<E> where() {
        return where;
    }

    @Override
    public AbstractGroupWrapper<E> where(IConditionWrapper<E> wrapper) {
        this.where = wrapper;
        return this;
    }

    @Override
    public AbstractGroupWrapper<E> orderBy(String name, boolean asc) {
        order.pushOrder(name,asc);
        return this;
    }

    @Override
    public AbstractGroupWrapper<E> page(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        return this;
    }

    public IConditionWrapper<E> getWhere() {
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
    public  AbstractGroupWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) {
        return orderBy(getName(name),asc);
    }

    @Override
    public  AbstractGroupWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) {
        return orderBy(name,false);
    }

    @Override
    public  AbstractGroupWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) {
        return orderBy(name,true);
    }

    //支持克隆
    @Override
    public AbstractGroupWrapper<E> clone()  {
        try {
            return (AbstractGroupWrapper<E>)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);

        }

    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractGroupWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractGroupWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public AbstractGroupWrapper<E> orderByAsc(String name) { orderBy(name, true); return this; }
    @Override public AbstractGroupWrapper<E> orderByDesc(String name) { orderBy(name, false); return this; }
}

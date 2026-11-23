package com.yulinlin.data.core.wrapper;


import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

//查询规范
public interface IGroupWrapper<E>

        extends IStoreWrapper<E,IGroupWrapper<E>> ,

        IWhereWrapper<E,IGroupWrapper<E>> ,
        IHavingWrapper<E,IGroupWrapper<E>>,IPageWrapper<IGroupWrapper<E>>,ISortWrapper<E,IGroupWrapper<E>>{


    IMetricsWrapper<E> metrics();

    IAggregationsWrapper<E> aggregations() ;

    





}

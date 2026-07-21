package com.yulinlin.data.core.wrapper;


import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

//查询规范
public interface IGroupWrapper<
        E,
        R extends IGroupWrapper<E,R,W,M,A>,
        W extends IConditionWrapper<E,W>,
        M extends IMetricsWrapper<E,M>,
        A extends IAggregationsWrapper<E,A>
        >

        extends IStoreWrapper<E,R,W> ,

        IWhereWrapper<R,W> ,
        IHavingWrapper<R,W>,IPageWrapper<R>,ISortWrapper<E,R>{


    M metrics();

    A aggregations() ;

    





}

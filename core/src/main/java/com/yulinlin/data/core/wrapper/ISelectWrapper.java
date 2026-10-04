package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

import java.util.function.Consumer;

//查询规范
public interface ISelectWrapper<
        E,
        R extends ISelectWrapper<E,R,W,C>,
        W extends IConditionWrapper<E,W>,
        C extends IAsFieldListWrapper<E,C>

        >
        extends IStoreWrapper<E,R,W>,   IWhereWrapper<R,W>,IPageWrapper<R>,ISortWrapper<E,R> {




    C  fields();


       R fields(Consumer<C> consumer);



    R lock();


}

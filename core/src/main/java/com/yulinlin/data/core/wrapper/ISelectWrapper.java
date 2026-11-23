package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

import java.util.function.Consumer;

//查询规范
public interface ISelectWrapper<E>
        extends IStoreWrapper<E,ISelectWrapper<E>>,   IWhereWrapper<E,ISelectWrapper<E>>,IPageWrapper<ISelectWrapper<E>>,ISortWrapper<E,ISelectWrapper<E>> {




    IAsFieldListWrapper<E>  fields();


       ISelectWrapper<E> fields(Consumer<IAsFieldListWrapper<E>> consumer);

    ISelectWrapper<E> highlight(String name);

    ISelectWrapper<E> highlight(LambdaPropertyFunction<E> name);



    ISelectWrapper<E> lock();


}

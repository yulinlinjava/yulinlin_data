package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

//更新规范
public interface IUpdateWrapper<E,
        R extends IUpdateWrapper<E,R,W,U>,
        W extends IConditionWrapper<E,W>,
        U  extends IUpdateFieldsWrapper<E,U>
        >

        extends   IExecuteWrapper<R> ,ITableWrapper<R>,
        IWhereWrapper<R,W>
{


     U fields();

     R fields(Consumer<U> func);




}

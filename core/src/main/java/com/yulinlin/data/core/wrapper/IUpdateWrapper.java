package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

//更新规范
public interface IUpdateWrapper<E>

        extends   IExecuteWrapper<IUpdateWrapper<E>> ,ITableWrapper<IUpdateWrapper<E>>,
        IWhereWrapper<E,IUpdateWrapper<E>>
{


     IUpdateFieldsWrapper<E> fields();

     IUpdateWrapper<E> fields(Consumer<IUpdateFieldsWrapper<E>> func);




}

package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

//插入规范
public interface IInsertWrapper<
        E,
        R extends IInsertWrapper<E,R,U>,
        U extends IInsertFieldsWrapper<E,U>

        > extends IExecuteWrapper<R>,ITableWrapper<R> {


    U fields();


     R fields(Consumer<U> func);

}

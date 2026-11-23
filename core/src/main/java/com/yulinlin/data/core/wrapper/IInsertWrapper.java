package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

//插入规范
public interface IInsertWrapper<E> extends IExecuteWrapper<IInsertWrapper<E>>,ITableWrapper<IInsertWrapper<E>> {


    IInsertFieldsWrapper<E> fields();


     IInsertWrapper<E> fields(Consumer<IInsertFieldsWrapper<E>> func);

}

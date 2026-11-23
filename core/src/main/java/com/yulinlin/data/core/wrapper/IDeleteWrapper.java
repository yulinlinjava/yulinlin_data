package com.yulinlin.data.core.wrapper;

//删除规范
public interface IDeleteWrapper<E>

        extends IExecuteWrapper<IDeleteWrapper<E>>,ITableWrapper<IDeleteWrapper<E>> ,

        IWhereWrapper<E,IDeleteWrapper<E>>{




}

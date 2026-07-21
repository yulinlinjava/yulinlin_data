package com.yulinlin.data.core.wrapper;

//删除规范
public interface IDeleteWrapper<
        E,
        R extends IDeleteWrapper<E,R,W> ,
        W extends IConditionWrapper<E,W>

        >

        extends IExecuteWrapper<R>,ITableWrapper<R> ,

        IWhereWrapper<R,W>{




}

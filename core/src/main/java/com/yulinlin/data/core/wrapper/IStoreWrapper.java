package com.yulinlin.data.core.wrapper;

//存储接口
public interface IStoreWrapper<E,R extends IStoreWrapper<E,R,W>,W extends IConditionWrapper<E,W>>  extends IJoinWrapper<E,R,W>,ITableWrapper<R> {
}

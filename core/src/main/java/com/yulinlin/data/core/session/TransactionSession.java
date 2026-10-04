package com.yulinlin.data.core.session;


public interface TransactionSession   {

    //开启事务
    void startTransaction();

    //提交事务
    void commitTransaction();

    //回滚事务
    void rollbackTransaction();

    boolean isOpenTransaction();

    /** Marks the current transaction as failed without prematurely closing its resources. */
    default void setRollbackOnly() { }

    default boolean isRollbackOnly() { return false; }


}

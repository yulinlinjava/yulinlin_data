package com.yulinlin.data.core.transaction;

public interface TransactionListener {

    //开启事务
    void startTransaction();

    //提交事务
    void commitTransaction();

    //回滚事务
    void rollbackTransaction();
}

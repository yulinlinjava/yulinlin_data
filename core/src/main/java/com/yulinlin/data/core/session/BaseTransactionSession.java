package com.yulinlin.data.core.session;

import java.util.concurrent.atomic.LongAdder;

 class BaseTransactionSession implements TransactionSession{


    private static ThreadLocal<LongAdder> transactionLocal = ThreadLocal.withInitial(() -> {
        return new LongAdder();
    });


    @Override
    public void startTransaction() {
        transactionLocal.get().increment();

    }

    @Override
    public void commitTransaction() {
        if(isOpenTransaction()){
            transactionLocal.get().decrement();
        }

    }

    @Override
    public void rollbackTransaction() {
        if(isOpenTransaction()){
            transactionLocal.get().decrement();
        }

    }

    @Override
    public boolean isOpenTransaction() {
        return  transactionLocal.get().intValue() > 0;
    }

}

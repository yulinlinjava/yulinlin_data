package com.yulinlin.data.core.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public class TransactionListenerManager {

    private List<TransactionListener> list ;


    public TransactionListenerManager() {
        this.list =  new ArrayList<>();
    }

    public TransactionListenerManager(List<TransactionListener> list) {
        this.list = list;
    }

    public TransactionListenerManager add(TransactionListener transactionService){
        list.add(transactionService);
        return this;
    }

    public TransactionListenerManager addAll(List<? extends TransactionListener> transactionServices){
        list.addAll(transactionServices);
        return this;
    }

    //开启事务
   public void startTransaction(){

       for (TransactionListener transactionService : list) {
           transactionService.startTransaction();
       }
   }

    //提交事务
    public void commitTransaction(){
        for (TransactionListener transactionService : list) {
            transactionService.commitTransaction();
        }

    }

    //回滚事务
   public void rollbackTransaction(){
       for (TransactionListener transactionService : list) {
           transactionService.rollbackTransaction();
       }
    }

}

package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

public interface IConditionObjectWrapper<R extends IConditionObjectWrapper<R>> {

    //一对一查询
    R object(String name);

    R object(String name, Class clazz);

    default R object(String name, Consumer<R> consumer){
        R r = object(name);
        consumer.accept(r);
        return (R)this;
    }



}

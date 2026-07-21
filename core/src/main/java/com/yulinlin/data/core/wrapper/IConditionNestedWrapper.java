package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

public interface IConditionNestedWrapper<R extends IConditionNestedWrapper<R>> {


    //嵌套列表查询
    R nested(String name);

    R nested(String name,Class clazz);

    default R nested(String name, Consumer<R> consumer){
        R r = nested(name);
        consumer.accept(r);
        return (R)this;
    }

}

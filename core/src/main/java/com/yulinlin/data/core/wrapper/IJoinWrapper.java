package com.yulinlin.data.core.wrapper;


import java.util.function.Consumer;

public interface IJoinWrapper<E,R extends IJoinWrapper<E,R,W>,W extends IConditionWrapper<E,W>> {

    R join(Consumer<R> func);

    W left(String name, String alias);

   W right(String name,String alias);

    W  inner(String name,String alias);


}

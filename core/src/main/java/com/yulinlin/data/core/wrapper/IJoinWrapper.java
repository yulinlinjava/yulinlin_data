package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

public interface IJoinWrapper<E, R> {
    R join(Consumer<R> consumer);
    IConditionWrapper<E> left(String name, String alias);
    IConditionWrapper<E> right(String name, String alias);
    IConditionWrapper<E> inner(String name, String alias);
}

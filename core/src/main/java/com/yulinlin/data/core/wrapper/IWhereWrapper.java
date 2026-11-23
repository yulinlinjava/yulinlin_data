package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

/** Shared condition operations; R is the owning wrapper's fluent return type. */
public interface IWhereWrapper<E, R> extends IWrapper {
    IConditionWrapper<E> where();
    R where(IConditionWrapper<E> wrapper);
    R where(Consumer<IConditionWrapper<E>> consumer);
}

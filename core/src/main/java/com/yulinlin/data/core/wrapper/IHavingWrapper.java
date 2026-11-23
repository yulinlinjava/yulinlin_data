package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

public interface IHavingWrapper<E, R> extends IWrapper {
    IConditionWrapper<E> having();
    R having(IConditionWrapper<E> wrapper);
    R having(Consumer<IConditionWrapper<E>> consumer);
}

package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

public interface IWhereWrapper<R extends IWhereWrapper<R,W>,W> extends IWrapper {


    W where();

    R where(W wrapper);

    R where(Consumer<W> func);



}

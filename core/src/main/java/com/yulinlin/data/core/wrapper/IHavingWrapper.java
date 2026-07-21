package com.yulinlin.data.core.wrapper;

import java.util.function.Consumer;

public interface IHavingWrapper<R extends IHavingWrapper<R,W>,W> extends IWrapper {


    W having();

    R having(W wrapper);

    R having(Consumer<W> func);



}

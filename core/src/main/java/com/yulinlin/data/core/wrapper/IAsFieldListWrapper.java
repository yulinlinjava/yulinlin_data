package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;



/**
 * 修改值用的
 * @param <E>
 * @param <R>
 */
public interface IAsFieldListWrapper<E,R extends IAsFieldListWrapper<E,R>> extends IMetaNode<R> {




    R field(String name);

    R field(String name, String alias);

    R field(LambdaPropertyFunction<E> name, String value);




}

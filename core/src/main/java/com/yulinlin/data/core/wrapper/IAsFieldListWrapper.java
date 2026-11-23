package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;



/**
 * 修改值用的
 * @param <E>
 */
public interface IAsFieldListWrapper<E> extends IMetaNode<IAsFieldListWrapper<E>> {




    IAsFieldListWrapper<E> field(String name);

    IAsFieldListWrapper<E> field(String name, String alias);

    IAsFieldListWrapper<E> field(LambdaPropertyFunction<E> name, String value);

    IAsFieldListWrapper<E> highlight(String name);

    IAsFieldListWrapper<E> highlight(LambdaPropertyFunction<E> name);




}

package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

/**
 * 修改值用的
 * @param <E>
 */
public interface IFieldsWrapper<E> extends IMetaNode<IFieldsWrapper<E>> {



    IFieldsWrapper<E> field(String name,Object value);

    IFieldsWrapper<E> field(LambdaPropertyFunction<E> name, Object value) ;




}

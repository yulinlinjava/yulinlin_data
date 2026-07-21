package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

/**
 * 修改值用的
 * @param <E>
 * @param <R>
 */
public interface IFieldsWrapper<E,R extends IFieldsWrapper<E,R>> extends IMetaNode<R> {



    R field(String name,Object value);

    R field(LambdaPropertyFunction<E> name, Object value) ;

    //MetaWrapper meta(String name);



}

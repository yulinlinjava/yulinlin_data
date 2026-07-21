package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

/**
 * 修改值用的
 * @param <E>
 * @param <R>
 */
public interface IUpdateFieldsWrapper<E,R extends IUpdateFieldsWrapper<E,R>> extends IFieldsWrapper<E,R>{





    R inc(String name, Number value);

    R inc(LambdaPropertyFunction<E> name, Number value) ;



    R dec(String name, Number value);

    R dec(LambdaPropertyFunction<E> name, Number value) ;




}

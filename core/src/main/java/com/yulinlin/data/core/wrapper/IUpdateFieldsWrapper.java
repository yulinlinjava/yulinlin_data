package com.yulinlin.data.core.wrapper;

import java.util.Map;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;



/**
 * 修改值用的
 * @param <E>
 */
public interface IUpdateFieldsWrapper<E> extends IFieldsWrapper<E>{





    IUpdateFieldsWrapper<E> inc(String name, Number value);

    IUpdateFieldsWrapper<E> inc(LambdaPropertyFunction<E> name, Number value) ;



    IUpdateFieldsWrapper<E> dec(String name, Number value);

    IUpdateFieldsWrapper<E> dec(LambdaPropertyFunction<E> name, Number value) ;





    @Override IUpdateFieldsWrapper<E> field(String name, Object value);
    @Override IUpdateFieldsWrapper<E> field(LambdaPropertyFunction<E> name, Object value);
    @Override IUpdateFieldsWrapper<E> meta(String name, Object value);
    @Override IUpdateFieldsWrapper<E> meta(Map<String, Object> values);
}

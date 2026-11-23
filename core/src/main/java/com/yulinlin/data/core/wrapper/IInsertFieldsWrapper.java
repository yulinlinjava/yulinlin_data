package com.yulinlin.data.core.wrapper;

import java.util.Map;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

/**
 * 修改值用的
 * @param <E>
 */
public interface IInsertFieldsWrapper<E> extends IFieldsWrapper<E> {





    @Override IInsertFieldsWrapper<E> field(String name, Object value);
    @Override IInsertFieldsWrapper<E> field(LambdaPropertyFunction<E> name, Object value);
    @Override IInsertFieldsWrapper<E> meta(String name, Object value);
    @Override IInsertFieldsWrapper<E> meta(Map<String, Object> values);
}

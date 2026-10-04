package com.yulinlin.data.core.wrapper;


import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.node.ICondition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

//条件规范
public interface IConditionWrapper<E,R extends IConditionWrapper<E,R>>  extends IMetaNode<R> {




    R condition(Object name, ConditionEnum condition, Object value);

    //自定义条件表达式
    //R expression(String name, Object... params);

    R expression(String name, Map<String,Object> data);

    default R expression(String name){
        return expression(name,new HashMap<>());
    }

    R eq(String name, Object value);

    R ne(String name, Object value);
    R gt(String name, Object value);

    R gte(String name, Object value);




    R lt(String name, Object value);

    R lte(String name, Object value);

    R like(String name, String value);

    R between(String name, Collection value);


    R likeRight(String name, String value);

    R isNull(String name);

    R in(String name, Collection<?> coll);

    R or(Consumer<R> consumer);

    R or();

    R and();



    R and(Consumer<R> consumer);

    R and(R conditionWrapper);

    R not();

    R not(Consumer<R> consumer);

    R struct();


    R nested(String key);

    R nested(String key,Consumer<R> consumer);

/*    R struct(Consumer<R> func);*/


    R eq(LambdaPropertyFunction<E> name, Object value);

    R ne(LambdaPropertyFunction<E> name, Object value);


    R gt(LambdaPropertyFunction<E> name, Object value) ;

    R gte(LambdaPropertyFunction<E> name, Object value);


      R lt(LambdaPropertyFunction<E> name, Object value) ;


      R lte(LambdaPropertyFunction<E> name, Object value);




      R between(LambdaPropertyFunction<E> name, Collection value);


      R like(LambdaPropertyFunction<E> name, String value);


      R likeRight(LambdaPropertyFunction<E> name, String value);


      R isNull(LambdaPropertyFunction<E> name) ;


      R in(LambdaPropertyFunction<E> name, Collection<?> coll);

    int getSize();

    ICondition getCondition();




}

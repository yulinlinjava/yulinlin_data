package com.yulinlin.data.core.wrapper;


import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.node.ICondition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Conditions return this interface; only the entity type is carried through the fluent API. */
public interface IConditionWrapper<E>  extends IMetaNode<IConditionWrapper<E>> {




    IConditionWrapper<E> condition(Object name, ConditionEnum condition, Object value);

    //自定义条件表达式

    IConditionWrapper<E> expression(String name, Map<String,Object> data);

    default IConditionWrapper<E> expression(String name){
        return expression(name,new HashMap<>());
    }

    IConditionWrapper<E> eq(String name, Object value);

    IConditionWrapper<E> ne(String name, Object value);
    IConditionWrapper<E> gt(String name, Object value);

    IConditionWrapper<E> gte(String name, Object value);




    IConditionWrapper<E> lt(String name, Object value);

    IConditionWrapper<E> lte(String name, Object value);

    IConditionWrapper<E> like(String name, String value);

    IConditionWrapper<E> match(String name, String value);

    IConditionWrapper<E> between(String name, Collection value);


    IConditionWrapper<E> likeRight(String name, String value);

    IConditionWrapper<E> isNull(String name);

    IConditionWrapper<E> in(String name, Collection<?> coll);

    IConditionWrapper<E> or(Consumer<IConditionWrapper<E>> consumer);

    IConditionWrapper<E> or();

    IConditionWrapper<E> and();



    IConditionWrapper<E> and(Consumer<IConditionWrapper<E>> consumer);

    IConditionWrapper<E> and(IConditionWrapper<?> conditionWrapper);

    IConditionWrapper<E> not();

    IConditionWrapper<E> not(Consumer<IConditionWrapper<E>> consumer);

    IConditionWrapper<E> struct();




    IConditionWrapper<E> eq(LambdaPropertyFunction<E> name, Object value);

    IConditionWrapper<E> ne(LambdaPropertyFunction<E> name, Object value);


    IConditionWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) ;

    IConditionWrapper<E> gte(LambdaPropertyFunction<E> name, Object value);


      IConditionWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) ;


      IConditionWrapper<E> lte(LambdaPropertyFunction<E> name, Object value);




      IConditionWrapper<E> between(LambdaPropertyFunction<E> name, Collection value);


      IConditionWrapper<E> like(LambdaPropertyFunction<E> name, String value);

      IConditionWrapper<E> match(LambdaPropertyFunction<E> name, String value);


      IConditionWrapper<E> likeRight(LambdaPropertyFunction<E> name, String value);


      IConditionWrapper<E> isNull(LambdaPropertyFunction<E> name) ;


      IConditionWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll);

    int getSize();

    ICondition getCondition();




}

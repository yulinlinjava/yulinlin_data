package com.yulinlin.data.core.node;


import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

public abstract class AbstractCondition<E> extends MetaNode  implements ICondition {




    private E value;

    public AbstractCondition(Object name, E value) {
        super(name);
        this.value = value;
    }

    public E getValue() {
        return value;
    }


}

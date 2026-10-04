package com.yulinlin.data.core.wrapper.impl;


import com.yulinlin.data.core.node.base.Nested;

import java.util.function.Consumer;

public final class ConditionManager<E> extends AbstractConditionWrapper<E, ConditionManager<E>> {
    public ConditionManager() {
    }

    public ConditionManager(String name) {
        super(name);
    }

    public ConditionManager<E> nested(String key){
        ConditionManager objectStringConditionManager = new ConditionManager(key);
        addNode(
                new Nested(key,objectStringConditionManager)
        );
        return   objectStringConditionManager;
    }


    public ConditionManager<E> nested(String key, Consumer<ConditionManager<E>> func){
        ConditionManager<E> nested = nested(key);
        func.accept(nested);
        return  this;
    }
}

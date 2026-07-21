package com.yulinlin.data.core.wrapper.impl;

public final class DeleteWrapper<E> extends AbstractDeleteWrapper<E, DeleteWrapper<E>,ConditionManager<E>> {


    public DeleteWrapper() {
    }

    @Override
    protected ConditionManager<E> buildWhere() {
        return new ConditionManager<>();
    }
}

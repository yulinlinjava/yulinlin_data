package com.yulinlin.data.core.wrapper.impl;


public final class UpdateWrapper<E> extends AbstractUpdateWrapper<
        E, UpdateWrapper<E>,ConditionManager<E>,
        UpdateFieldsWrapper<E>
                > {

    public UpdateWrapper() {
    }

    @Override
    protected ConditionManager<E> buildWhere() {
        return new ConditionManager();
    }

    @Override
    protected UpdateFieldsWrapper<E> buildColumns() {
        return new UpdateFieldsWrapper<>();
    }
}

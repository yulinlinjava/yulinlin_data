package com.yulinlin.data.core.wrapper.impl;


public final class InsertWrapper<E> extends AbstractInsertWrapper<E, InsertWrapper<E>,InsertFieldsWrapper<E>> {


    public InsertWrapper() {
    }

    @Override
    protected InsertFieldsWrapper<E> buildColumns() {
        return new InsertFieldsWrapper();
    }
}


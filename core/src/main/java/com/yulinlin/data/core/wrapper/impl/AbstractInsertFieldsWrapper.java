package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.wrapper.IInsertFieldsWrapper;

public abstract class AbstractInsertFieldsWrapper<E, R extends AbstractInsertFieldsWrapper<E, R>>
        extends AbstractFieldsWrapper<E, R> implements IInsertFieldsWrapper<E, R> {
}

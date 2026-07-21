package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.wrapper.IInsertFieldsWrapper;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractInsertFieldsWrapper<E,R extends AbstractInsertFieldsWrapper<E,R>>
        extends AbstractFieldsWrapper<E,R>

        implements IInsertFieldsWrapper<E,R>

{


    private String name;

    private Map<String,R> children = new HashMap<>();




    public AbstractInsertFieldsWrapper() {
    }

    public AbstractInsertFieldsWrapper(String name) {
        super(name);
    }





}

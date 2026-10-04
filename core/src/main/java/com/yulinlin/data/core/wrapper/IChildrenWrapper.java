package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.wrapper.impl.AbstractWrapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class IChildrenWrapper<R> extends AbstractWrapper<R> implements IWrapper {

    private String name;



    public IChildrenWrapper() {

    }



    public IChildrenWrapper(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


}

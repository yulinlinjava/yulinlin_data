package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

public  class ClassUpdateWrapperFactory extends AbstractUpdateWrapperFactory {

    private Class<? extends IUpdateWrapper> clazz;

    public ClassUpdateWrapperFactory(Class<? extends IUpdateWrapper> clazz) {
        this.clazz = clazz;
    }

    @Override
    public IUpdateWrapper create() {
        return ReflectionUtil.newInstance(clazz);
    }
}

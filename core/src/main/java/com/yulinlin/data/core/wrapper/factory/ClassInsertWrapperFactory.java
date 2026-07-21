package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.wrapper.IInsertWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

public  class ClassInsertWrapperFactory extends AbstractInsertWrapperFactory {

    private Class<? extends IInsertWrapper> clazz;

    public ClassInsertWrapperFactory(Class<? extends IInsertWrapper> clazz) {
        this.clazz = clazz;
    }

    @Override
    public IInsertWrapper create() {
        return ReflectionUtil.newInstance(clazz);
    }
}

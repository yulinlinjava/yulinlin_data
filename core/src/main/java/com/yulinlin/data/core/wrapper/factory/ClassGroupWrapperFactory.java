package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

public  class ClassGroupWrapperFactory extends AbstractGroupWrapperFactory {

    private Class<? extends IGroupWrapper> clazz;

    public ClassGroupWrapperFactory(Class<? extends IGroupWrapper> clazz) {
        this.clazz = clazz;
    }

    @Override
    public IGroupWrapper create() {
        return ReflectionUtil.newInstance(clazz);
    }
}

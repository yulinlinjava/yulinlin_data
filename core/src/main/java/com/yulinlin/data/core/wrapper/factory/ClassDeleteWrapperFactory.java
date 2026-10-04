package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.wrapper.IDeleteWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

public  class ClassDeleteWrapperFactory extends AbstractDeleteWrapperFactory {

    private Class<? extends IDeleteWrapper> clazz;

    public ClassDeleteWrapperFactory(Class<? extends IDeleteWrapper> clazz) {
        this.clazz = clazz;
    }

    @Override
    public IDeleteWrapper create() {
        return ReflectionUtil.newInstance(clazz);
    }
}

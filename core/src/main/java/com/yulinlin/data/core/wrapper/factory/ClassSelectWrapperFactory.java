package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.wrapper.ISelectWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

public  class ClassSelectWrapperFactory extends AbstractSelectWrapperFactory  {


    private Class<? extends ISelectWrapper> clazz;

    public ClassSelectWrapperFactory(Class<? extends ISelectWrapper> clazz) {
        this.clazz = clazz;
    }

    @Override
    public ISelectWrapper create() {
        return ReflectionUtil.newInstance(clazz);
    }
}

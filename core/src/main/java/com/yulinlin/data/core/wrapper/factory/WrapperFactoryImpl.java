package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.wrapper.*;
import com.yulinlin.data.core.wrapper.impl.*;
import lombok.Builder;


@Builder
public  class WrapperFactoryImpl implements IWrapperFactory {

    private AbstractDeleteWrapperFactory delete;

    private AbstractInsertWrapperFactory insert;

    private AbstractUpdateWrapperFactory update;

    private AbstractGroupWrapperFactory group;

    private AbstractSelectWrapperFactory select;

    @Override
    public ICountWrapper createCountWrapper(INode node) {
        return new CountWrapper(node);
    }

    @Override
    public <E> IDeleteWrapper<E> createDeleteWrapper(Object model) {
        return delete.create(model);
    }

    @Override
    public <E> IInsertWrapper<E> createInsertWrapper(Object model) {
        return insert.create(model);
    }

    @Override
    public <E> IUpdateWrapper<E> createUpdateWrapper(Object model) {
        return update.create(model);
    }

    @Override
    public <E> ISelectWrapper<E> createSelectWrapper(Object model) {
        return select.create(model);
    }

    @Override
    public <E> IGroupWrapper<E> createGroupWrapper(Object model) {
        return group.create(model);
    }

    @Override
    public <E> IDeleteWrapper<E> createDeleteWrapper() {
        return delete.create();
    }

    @Override
    public <E> IInsertWrapper<E> createInsertWrapper() {
        return insert.create();
    }

    @Override
    public <E> IUpdateWrapper<E> createUpdateWrapper() {
        return update.create();
    }

    @Override
    public <E> ISelectWrapper<E> createSelectWrapper() {
        return select.create();
    }

    @Override
    public <E> IGroupWrapper<E> createGroupWrapper() {
        return group.create();
    }


    public static WrapperFactoryImpl newInstance(
           Class... classes
    ){
        return WrapperFactoryImpl.builder()
                .insert(new ClassInsertWrapperFactory(classes[0]))
                .update(new ClassUpdateWrapperFactory(classes[1]))
                .delete(new ClassDeleteWrapperFactory(classes[2]))
                .select(new ClassSelectWrapperFactory(classes[3]))
                .group(new ClassGroupWrapperFactory(classes[4]))
                .build();
    }
}

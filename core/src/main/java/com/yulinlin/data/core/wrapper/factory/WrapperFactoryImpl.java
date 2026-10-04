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
    public IDeleteWrapper createDeleteWrapper(Object model) {
        return delete.create(model);
    }

    @Override
    public IInsertWrapper createInsertWrapper(Object model) {
        return insert.create(model);
    }

    @Override
    public IUpdateWrapper createUpdateWrapper(Object model) {
        return update.create(model);
    }

    @Override
    public ISelectWrapper createSelectWrapper(Object model) {
        return select.create(model);
    }

    @Override
    public IGroupWrapper createGroupWrapper(Object model) {
        return group.create(model);
    }

    @Override
    public <E, R extends IDeleteWrapper<E, R, W>, W extends IConditionWrapper<E, W>> IDeleteWrapper<E, R, W> createDeleteWrapper() {
        return delete.create();
    }

    @Override
    public <E, R extends IInsertWrapper<E, R, U>, U extends IInsertFieldsWrapper<E, U>> IInsertWrapper<E, R, U> createInsertWrapper() {
        return insert.create();
    }

    @Override
    public <E, R extends IUpdateWrapper<E, R, W, U>, W extends IConditionWrapper<E, W>, U extends IUpdateFieldsWrapper<E, U>> IUpdateWrapper<E, R, W, U> createUpdateWrapper() {
        return update.create();
    }

    @Override
    public <E, R extends ISelectWrapper<E, R, W, C>, W extends IConditionWrapper<E, W>, C extends IAsFieldListWrapper<E, C>> ISelectWrapper<E, R, W, C> createSelectWrapper() {
        return select.create();
    }

    @Override
    public <E, R extends IGroupWrapper<E, R, W, M, A>, W extends IConditionWrapper<E, W>, M extends IMetricsWrapper<E, M>, A extends IAggregationsWrapper<E, A>> IGroupWrapper<E, R, W, M, A> createGroupWrapper() {
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

package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.INode;

public interface IWrapperFactory {


    ICountWrapper createCountWrapper(INode node);

     IDeleteWrapper createDeleteWrapper(Object model);

    IInsertWrapper createInsertWrapper(Object model);

    IUpdateWrapper createUpdateWrapper(Object model);

    ISelectWrapper createSelectWrapper(Object model);

    IGroupWrapper createGroupWrapper(Object model);



    <
            E,R extends IDeleteWrapper<E,R,W> ,W extends IConditionWrapper<E,W>

            > IDeleteWrapper<E,R,W> createDeleteWrapper();

    <
            E,
            R extends IInsertWrapper<E,R,U>,
            U extends IInsertFieldsWrapper<E,U>

            > IInsertWrapper<E,R,U> createInsertWrapper();

    <E,
            R extends IUpdateWrapper<E,R,W,U>,
            W extends IConditionWrapper<E,W>,
            U  extends IUpdateFieldsWrapper<E,U>
            >  IUpdateWrapper<E,R,W,U> createUpdateWrapper();

    <
            E,
            R extends ISelectWrapper<E,R,W,C>,
            W extends IConditionWrapper<E,W>,
            C extends IAsFieldListWrapper<E,C>
            > ISelectWrapper<E,R,W,C> createSelectWrapper();

    <
            E,
            R extends IGroupWrapper<E,R,W,M,A>,
            W extends IConditionWrapper<E,W>,
            M extends IMetricsWrapper<E,M>,
            A extends IAggregationsWrapper<E,A>
            >  IGroupWrapper<E,R,W,M,A> createGroupWrapper();

}

package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.wrapper.factory.UpdatePatchFactory;
import java.util.Map;

public interface IWrapperFactory {


    ICountWrapper createCountWrapper(INode node);

     <E> IDeleteWrapper<E> createDeleteWrapper(Object model);

    <E> IInsertWrapper<E> createInsertWrapper(Object model);

    <E> IUpdateWrapper<E> createUpdateWrapper(Object model);

    /** Setter-tracked update; original keys, not current entity defaults, form the WHERE clause. */
    default <E> IUpdateWrapper<E> createUpdateWrapper(Object model, Map<String, Object> fields,
                                               Map<String, Object> primaryKeys, Map<String, Object> versions) {
        return UpdatePatchFactory.create(createUpdateWrapper(), model, fields, primaryKeys, versions);
    }

    <E> ISelectWrapper<E> createSelectWrapper(Object model);

    <E> IGroupWrapper<E> createGroupWrapper(Object model);



    <E> IDeleteWrapper<E> createDeleteWrapper();

    <E> IInsertWrapper<E> createInsertWrapper();

    <E> IUpdateWrapper<E> createUpdateWrapper();

    <E> ISelectWrapper<E> createSelectWrapper();

    <E> IGroupWrapper<E> createGroupWrapper();

}

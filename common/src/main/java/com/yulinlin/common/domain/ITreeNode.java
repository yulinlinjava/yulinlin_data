package com.yulinlin.common.domain;

import java.util.Comparator;

public interface ITreeNode<E extends ITreeNode> {

    String getId();

    String getParentId();

    void addChildren(E node);

    default void sort(Comparator<ITreeNode> comparator){

    }

}

package com.yulinlin.common.service;


import com.yulinlin.common.domain.ITreeNode;
import com.yulinlin.common.model.AbstractModel;

import java.util.List;

public interface ITreeService<E extends ITreeNode<E> & AbstractModel<E>> extends ISuperService<E> {


    /**
     * 得到一个树
     * @return
     */
    List<E> getTree(Object query);


}

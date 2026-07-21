package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.IMetaNode;

public interface ITableWrapper<R> extends IMetaNode<R> {


    R table(String name);

    R table(String name, String alias);



}

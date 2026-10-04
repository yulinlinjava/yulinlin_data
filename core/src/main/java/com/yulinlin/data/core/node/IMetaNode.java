package com.yulinlin.data.core.node;

import java.util.Map;

public interface IMetaNode<R> extends INode {

    R meta(String name,Object value);

     R meta(Map<String,Object> map);
}

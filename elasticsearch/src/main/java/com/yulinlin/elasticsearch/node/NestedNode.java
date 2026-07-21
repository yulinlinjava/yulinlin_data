package com.yulinlin.elasticsearch.node;

import com.yulinlin.data.core.node.AbstractCondition;
import com.yulinlin.data.core.node.INode;

public class NestedNode extends AbstractCondition<INode> {

    public NestedNode(Object key, INode value) {
        super(key, value);
    }

}

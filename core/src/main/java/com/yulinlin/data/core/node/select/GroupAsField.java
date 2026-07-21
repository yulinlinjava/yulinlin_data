package com.yulinlin.data.core.node.select;

import com.yulinlin.data.core.node.IGroupNode;
import com.yulinlin.data.core.node.INode;

public class GroupAsField implements INode {

    private IGroupNode group;

    private String alias;

    public GroupAsField(IGroupNode group, String alias) {
        this.group = group;
        this.alias = alias;
    }

    public IGroupNode getGroup() {
        return group;
    }

    public String getAlias() {
        return alias;
    }
}

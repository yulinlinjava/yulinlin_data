package com.yulinlin.data.core.node.select;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.MetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

public class AsField extends MetaNode implements INode {


    private String alias;

    public AsField(Object name, String alias) {
        super(name);
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }


}

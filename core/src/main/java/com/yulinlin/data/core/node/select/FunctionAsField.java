package com.yulinlin.data.core.node.select;

import com.yulinlin.data.core.node.AbstractMetaNode;
import com.yulinlin.data.core.node.metrics.AbstractMetrics;

public class FunctionAsField extends AbstractMetaNode {

    private AbstractMetrics function;

    private String alias;

    public FunctionAsField(AbstractMetrics function, String alias) {
        this.function = function;
        this.alias = alias;
    }

    public AbstractMetrics getFunction() {
        return function;
    }

    public String getAlias() {
        return alias;
    }
}

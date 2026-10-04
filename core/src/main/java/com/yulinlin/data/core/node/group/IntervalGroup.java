package com.yulinlin.data.core.node.group;

import com.yulinlin.data.core.node.MetaNode;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

public class IntervalGroup extends MetaNode implements IRangeGroup {



    private int interval;

    public IntervalGroup(Object name, int interval) {
        super(name);
        this.interval = interval;
    }

    public int getInterval() {
        return interval;
    }
}

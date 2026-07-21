package com.yulinlin.data.core.node.base;

import com.yulinlin.data.core.node.AbstractCondition;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

import java.util.Collection;

public class Between extends AbstractCondition<Collection> {

    public Between(Object name, Collection value) {
        super(name, value);
    }
}

package com.yulinlin.data.core.parse;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.lang.reflection.GenericUtil;

public interface IParse<E extends INode> {

    Object parse(E condition, IParamsContext params, IParseManager parseManager);

    default Class getNodeClass(){
        return GenericUtil.getGeneric(this.getClass(),IParse.class,0);
    }
}

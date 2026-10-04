package com.yulinlin.data.core.parse;

import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.node.INode;

import java.util.Collection;

public interface IParseManager {

    /**
     * 解析一个节点
     * @param node
     * @param params
     * @return
     */
    Object parse(INode node,IParamsContext params);



}

package com.yulinlin.data.core.wrapper.impl;


import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.wrapper.ICountWrapper;

public class CountWrapper implements ICountWrapper {

    private INode wrapper;

    public CountWrapper(INode wrapper) {
        this.wrapper = wrapper;
    }


    public INode getWrapper() {
        return wrapper;
    }
}

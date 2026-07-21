package com.yulinlin.data.core.node.from;

import com.yulinlin.data.core.node.INode;

public class Join implements From {

    private From left;

    private From  right;

    private JoinEnum joinEnum;

    private INode on;

    public Join(From left, From right, JoinEnum joinEnum, INode on) {
        this.left = left;
        this.right = right;
        this.joinEnum = joinEnum;
        this.on = on;
    }

    public INode getOn() {
        return on;
    }

    public JoinEnum getJoinEnum() {
        return joinEnum;
    }


    public From getLeft() {
        return left;
    }

    public From getRight() {
        return right;
    }

    public static enum JoinEnum{
        left,
        right,
        inner
    }
}

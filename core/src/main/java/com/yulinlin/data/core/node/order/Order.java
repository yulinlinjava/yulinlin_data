package com.yulinlin.data.core.node.order;

import com.yulinlin.data.core.node.INode;

import java.util.ArrayList;
import java.util.List;

public class Order implements INode {

    private List<OrderNode> list;

    public Order() {
        this.list = new ArrayList<>();
    }

    public void pushOrder(Object name, boolean asc){
        list.add(new OrderNode(
                name,asc
        ));
    }

    public List<OrderNode> getList() {
        return list;
    }
}

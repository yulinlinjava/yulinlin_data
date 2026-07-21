package com.yulinlin.data.core.node.order;

import com.yulinlin.data.core.node.MetaNode;
import lombok.Data;

@Data
public class OrderNode extends MetaNode {



    private boolean asc;

    public OrderNode(Object name, boolean asc) {
        super(name);
        this.asc = asc;
    }



    public boolean isAsc() {
        return asc;
    }
}

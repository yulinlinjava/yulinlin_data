package com.yulinlin.elasticsearch.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractCollectionCoder;

import java.util.*;


class CollectionCoder extends AbstractCollectionCoder<Collection> {


    @Override
    public Collection encode(IDataBuffer buffer, String key, Collection value) {
        return value;
    }
}

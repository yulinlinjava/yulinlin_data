package com.yulinlin.mongodb.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractCollectionCoder;

import java.util.Collection;


class CollectionCoder extends AbstractCollectionCoder<Collection> {


    @Override
    public Collection encode(IDataBuffer buffer, String key, Collection value) {
        return value;
    }
}

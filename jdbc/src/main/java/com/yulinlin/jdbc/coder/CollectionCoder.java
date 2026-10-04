package com.yulinlin.jdbc.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractCollectionCoder;
import com.yulinlin.data.lang.json.JsonUtil;

import java.util.*;


class CollectionCoder extends AbstractCollectionCoder<String> {


    @Override
    public String encode(IDataBuffer buffer, String key, Collection value) {
        return JsonUtil.toJson(value);


    }


}

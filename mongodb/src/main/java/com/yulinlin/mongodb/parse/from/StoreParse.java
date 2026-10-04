package com.yulinlin.mongodb.parse.from;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

public class StoreParse implements IParse<Store> {

    @Override
    public String parse(Store condition, IParamsContext params, IParseManager parseManager) {

        return  condition.getName();
    }
}

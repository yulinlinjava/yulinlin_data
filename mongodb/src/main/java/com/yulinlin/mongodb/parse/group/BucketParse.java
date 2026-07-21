package com.yulinlin.mongodb.parse.group;


import com.yulinlin.data.core.node.group.BucketGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;

public class BucketParse implements IParse<BucketGroup> {




    @Override
    public Object parse(BucketGroup condition, IParamsContext params, IParseManager parseManager) {
        String key =AliasUtil.parse(condition,params);
        return BsonUtil.toBsonKey(key);
    }


}

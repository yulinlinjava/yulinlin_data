package com.yulinlin.jdbc.postgresql.parse.group;

import com.yulinlin.data.core.node.group.BucketGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;

public class BucketParse implements IParse<BucketGroup> {




    @Override
    public Object parse(BucketGroup condition, IParamsContext params, IParseManager parseManager) {

        String key = AliasUtil.parse(condition,params) ;
        return key;

    }


}

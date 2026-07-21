package com.yulinlin.jdbc.mysql.parse.base;

import com.yulinlin.data.core.node.base.Nil;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

public class NilParse implements IParse<Nil> {

    @Override
    public String parse(Nil condition, IParamsContext params, IParseManager parseManager) {
        return null;
    }
}

package com.yulinlin.mongodb.parse.base;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.ConditionManager;

public class MongoConditionWrapperParse implements IParse<ConditionManager> {

    @Override
    public Object parse(ConditionManager condition, IParamsContext params, IParseManager parseManager) {

            return parseManager.parse( condition.getCondition(),params);



    }
}

package com.yulinlin.jdbc.sql.parse.statement;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.ConditionManager;

public class SqlConditionWrapperParse implements IParse<ConditionManager> {
    @Override
    public Object parse(ConditionManager condition, IParamsContext params, IParseManager manager) {
        return manager.parse(condition.getCondition(), params);
    }
}

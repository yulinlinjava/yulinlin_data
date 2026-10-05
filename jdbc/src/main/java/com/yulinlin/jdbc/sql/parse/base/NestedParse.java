package com.yulinlin.jdbc.sql.parse.base;

import com.yulinlin.data.core.node.base.Nested;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

/** Nested JSON conditions reuse the ConditionManager path context. */
public class NestedParse implements IParse<Nested> {
    @Override public Object parse(Nested node, IParamsContext params, IParseManager manager) {
        return manager.parse(node.getValue(), params);
    }
}

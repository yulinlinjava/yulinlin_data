package com.yulinlin.data.core.node.base;

import com.yulinlin.data.core.node.AbstractCondition;

/** Database-neutral full-text condition; unsupported stores may fall back to LIKE semantics. */
public class Match extends AbstractCondition<String> {
    public Match(Object name, String value) {
        super(name, value);
    }
}

package com.yulinlin.jdbc.postgresql.parse.predicate;

import com.yulinlin.data.core.node.predicate.Or;

public class OrParse extends PredicatesParse<Or> {
    private static String key=" or ";

    @Override
    public String getSeparator() {
        return key;
    }
}

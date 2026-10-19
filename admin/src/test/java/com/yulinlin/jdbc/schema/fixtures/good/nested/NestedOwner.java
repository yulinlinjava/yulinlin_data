package com.yulinlin.jdbc.schema.fixtures.good.nested;

import com.yulinlin.data.core.anno.JoinTable;

@JoinTable(value = "nested_table", autoSchema = true)
public class NestedOwner {
    private String value;
}

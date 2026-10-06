package com.yulinlin.jdbc.schema.fixtures.good;

import com.yulinlin.data.core.anno.JoinTable;

@JoinTable(value = "owned_table", autoSchema = false)
public class Projection {
    private String id;
}

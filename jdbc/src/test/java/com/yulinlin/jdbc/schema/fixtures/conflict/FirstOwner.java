package com.yulinlin.jdbc.schema.fixtures.conflict;

import com.yulinlin.data.core.anno.JoinTable;

@JoinTable(value = "shared_table", autoSchema = true)
public class FirstOwner {
    private String first;
}

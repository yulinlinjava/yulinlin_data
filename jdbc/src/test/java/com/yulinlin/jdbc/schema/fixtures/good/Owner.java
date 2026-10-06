package com.yulinlin.jdbc.schema.fixtures.good;

import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;

@JoinTable(value = "owned_table", autoSchema = true)
public class Owner {
    @JoinMeta(primaryKey = true)
    private String id;
}

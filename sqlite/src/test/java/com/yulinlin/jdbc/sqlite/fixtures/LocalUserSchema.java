package com.yulinlin.jdbc.sqlite.fixtures;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;

/** The single startup schema owner for local_user in SQLite integration tests. */
@JoinTable(value = "local_user", autoSchema = true)
public class LocalUserSchema {
    @JoinMeta(primaryKey = true)
    private String id;
    @JoinField(name = "user_name")
    private String name;
    private Integer status;
}

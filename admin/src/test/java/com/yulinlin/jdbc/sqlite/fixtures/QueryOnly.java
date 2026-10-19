package com.yulinlin.jdbc.sqlite.fixtures;

import com.yulinlin.data.core.anno.JoinTable;

@JoinTable(left = "schema_entity a", right = "schema_entity b", on = "a.id=b.id", autoSchema = true)
public class QueryOnly {}

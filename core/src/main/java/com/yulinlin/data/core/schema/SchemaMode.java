package com.yulinlin.data.core.schema;

/** Controls startup initialization of an entity's physical storage structure. */
public enum SchemaMode {
    /** Do not scan, create, or validate storage structures. */
    NONE,
    /** Create missing structures and validate existing ones; opted-in JDBC modules may also alter column types. */
    CREATE,
    /** Validate existing structures without changing them. */
    VALIDATE
}

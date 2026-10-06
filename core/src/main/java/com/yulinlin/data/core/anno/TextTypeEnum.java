package com.yulinlin.data.core.anno;

/** Portable text storage requested by {@link JoinField}. */
public enum TextTypeEnum {
    /** Keep the Java-type and database-specific default. */
    auto,
    /** Store text in a bounded VARCHAR; textLength optionally supplies the character limit. */
    varchar,
    /** Store large text in the database's native large-text type. */
    text
}

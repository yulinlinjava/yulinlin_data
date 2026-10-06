package com.yulinlin.jdbc.schema;

import java.util.Collection;
import java.util.List;

/** @deprecated Use {@link com.yulinlin.data.core.schema.SchemaEntityScanner}. */
@Deprecated
public final class SchemaEntityScanner {
    private SchemaEntityScanner() { }

    public static List<Class<?>> scan(Collection<String> packages) {
        return com.yulinlin.data.core.schema.SchemaEntityScanner.scan(packages);
    }
}

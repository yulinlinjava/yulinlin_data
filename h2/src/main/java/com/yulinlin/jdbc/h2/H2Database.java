package com.yulinlin.jdbc.h2;

import javax.sql.DataSource;

/** Owns the local H2 pool; neither DataSource is exposed as a Spring bean. */
public final class H2Database implements AutoCloseable {
    private final H2DataSource dataSource;

    public H2Database(H2Properties properties) {
        dataSource = new H2DataSource(properties);
    }

    H2DataSource dataSource() { return dataSource; }
    DataSource schemaDataSource() { return dataSource.directDataSource(); }

    @Override public void close() { dataSource.close(); }
}

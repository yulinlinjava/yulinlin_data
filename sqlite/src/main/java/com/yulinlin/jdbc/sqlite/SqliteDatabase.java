package com.yulinlin.jdbc.sqlite;

/** Owns the local connection pool; its DataSource is never registered as a Spring bean. */
public final class SqliteDatabase implements AutoCloseable {
    private final SqliteDataSource dataSource;

    public SqliteDatabase(SqliteProperties properties) {
        this.dataSource = new SqliteDataSource(properties);
    }

    SqliteDataSource dataSource() { return dataSource; }

    @Override public void close() { dataSource.close(); }
}

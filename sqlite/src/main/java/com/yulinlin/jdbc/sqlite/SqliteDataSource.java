package com.yulinlin.jdbc.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** One pooled connection per local database: predictable writes, no writer-pool contention. */
public final class SqliteDataSource extends HikariDataSource {
    public SqliteDataSource(SqliteProperties properties) {
        super(config(properties));
        try (Connection connection = getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA journal_mode=WAL")) {
            if (!result.next() || !"wal".equalsIgnoreCase(result.getString(1))) {
                throw new IllegalStateException("SQLite database did not enable WAL");
            }
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Cannot initialize SQLite WAL", e);
        }
    }

    private static HikariConfig config(SqliteProperties p) {
        if (p.getFile() == null || p.getFile().isBlank() || p.getFile().contains(":memory:")
                || p.getFile().startsWith("file:") || p.getFile().startsWith("jdbc:")) {
            throw new IllegalArgumentException("yulinlin.sqlite.file must be a local file path, not a JDBC/URI/memory URL");
        }
        if (p.getGroup() == null || p.getGroup().isBlank() || p.getBusyTimeout() < 0 || p.getSynchronous() == null) {
            throw new IllegalArgumentException("SQLite group, busy-timeout or synchronous is invalid");
        }
        Path path = Path.of(p.getFile()).toAbsolutePath().normalize();
        try { Files.createDirectories(path.getParent()); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot create SQLite directory: " + path.getParent(), e); }
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.setSynchronous(SQLiteConfig.SynchronousMode.valueOf(p.getSynchronous().name()));
        sqlite.setBusyTimeout(p.getBusyTimeout());
        sqlite.enforceForeignKeys(true);
        SQLiteDataSource delegate = new SQLiteDataSource(sqlite);
        delegate.setUrl("jdbc:sqlite:" + path.toUri());
        HikariConfig pool = new HikariConfig();
        pool.setDataSource(delegate);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(1);
        pool.setPoolName("sqlite-" + p.getGroup());
        pool.setConnectionTimeout(Math.max(1000L, p.getBusyTimeout() + 1000L));
        return pool;
    }
}

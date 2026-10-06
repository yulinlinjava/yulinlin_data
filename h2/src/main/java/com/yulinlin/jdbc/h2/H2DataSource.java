package com.yulinlin.jdbc.h2;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

/** Local H2 pool plus an unpooled connection source reserved for schema DDL. */
public final class H2DataSource extends HikariDataSource {
    private record Sources(HikariConfig pool, JdbcDataSource direct) { }

    private final JdbcDataSource direct;

    public H2DataSource(H2Properties properties) {
        this(sources(properties));
    }

    private H2DataSource(Sources sources) {
        super(sources.pool());
        direct = sources.direct();
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        } catch (Exception error) {
            close();
            throw new IllegalStateException("Cannot initialize H2 database", error);
        }
    }

    DataSource directDataSource() { return direct; }

    private static Sources sources(H2Properties properties) {
        validate(properties);
        Path file = Path.of(properties.getFile()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot create H2 directory: " + file.getParent(), error);
        }

        String path = file.toString().replace('\\', '/');
        StringBuilder url = new StringBuilder("jdbc:h2:file:").append(path)
                .append(";MODE=").append(properties.getMode().jdbcValue())
                .append(";DATABASE_TO_LOWER=TRUE")
                .append(";CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .append(";DB_CLOSE_ON_EXIT=FALSE")
                .append(";LOCK_TIMEOUT=").append(properties.getLockTimeout().toMillis());
        if (properties.isAutoServer()) url.append(";AUTO_SERVER=TRUE");

        JdbcDataSource delegate = new JdbcDataSource();
        delegate.setURL(url.toString());
        delegate.setUser(properties.getUsername());
        delegate.setPassword(properties.getPassword() == null ? "" : properties.getPassword());

        HikariConfig pool = new HikariConfig();
        pool.setDataSource(delegate);
        pool.setMaximumPoolSize(properties.getMaxConnections());
        pool.setMinimumIdle(1);
        pool.setPoolName("h2-" + properties.getGroup());
        pool.setConnectionTimeout(Math.max(250L, properties.getConnectionTimeout().toMillis()));
        return new Sources(pool, delegate);
    }

    private static void validate(H2Properties properties) {
        String file = properties.getFile();
        if (file == null || file.isBlank() || file.indexOf(';') >= 0
                || file.startsWith("jdbc:") || file.startsWith("mem:")
                || file.contains(":memory:") || file.toLowerCase(java.util.Locale.ROOT).endsWith(".mv.db")) {
            throw new IllegalArgumentException(
                    "yulinlin.h2.file must be a local database base path without jdbc:, mem: or .mv.db");
        }
        if (properties.getGroup() == null || properties.getGroup().isBlank()
                || properties.getUsername() == null || properties.getMode() != H2Properties.Mode.MYSQL
                || properties.getMaxConnections() < 1 || properties.getBatchSize() < 1
                || properties.getConnectionTimeout() == null || properties.getConnectionTimeout().isNegative()
                || properties.getConnectionTimeout().isZero()
                || properties.getLockTimeout() == null || properties.getLockTimeout().isNegative()
                || properties.getLockTimeout().toMillis() > Integer.MAX_VALUE
                || properties.getSchemaMode() == null) {
            throw new IllegalArgumentException("Invalid yulinlin.h2 configuration");
        }
    }
}

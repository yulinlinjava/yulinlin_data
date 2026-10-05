package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.session.JdbcSession;
import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** Shared JDBC execution with PostgreSQL parsers and driver-specific value handling. */
public class PostgresqlSession extends JdbcSession {
    public PostgresqlSession(DataSource dataSource) {
        super(dataSource);
        setParseManager(new PostgresqlParseManager());
    }

    @Override protected void bindParameter(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof InputStream stream) statement.setBinaryStream(index, stream);
        else statement.setObject(index, value);
    }

    @Override protected Object readColumn(ResultSet rows, String label, int jdbcType) throws SQLException {
        if (jdbcType == Types.BOOLEAN || jdbcType == Types.BIT) {
            boolean value = rows.getBoolean(label);
            return rows.wasNull() ? null : value;
        }
        return rows.getString(label);
    }
}

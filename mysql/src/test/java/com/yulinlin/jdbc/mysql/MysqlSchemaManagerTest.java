package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.TextTypeEnum;
import com.yulinlin.jdbc.JdbcProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MysqlSchemaManagerTest {
    @Test void sessionExposesTheSameInitialDdlWithoutOpeningAConnection() {
        DataSource dataSource = mock(DataSource.class);
        var session = new MysqlSession(dataSource);
        session.setProperties(new JdbcProperties());

        assertThat(session.createTableSql(PreviewUser.class))
                .anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"))
                .anyMatch(sql -> sql.contains("COMMENT 'Display name'"))
                .anyMatch(sql -> sql.startsWith("CREATE INDEX"));
        verifyNoInteractions(dataSource);
    }

    @Test void stringPrimaryKeyIsLimitedTo128CharactersAndCannotBeLargeText() {
        Connection connection = mock(Connection.class);
        var manager = new MysqlSchemaManager();

        assertThatThrownBy(() -> manager.ensureTable(connection, TooLongPrimary.class, true,
                MysqlProperties.SchemaMode.CREATE, sql -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 128");
        assertThatThrownBy(() -> manager.ensureTable(connection, LargeTextPrimary.class, true,
                MysqlProperties.SchemaMode.CREATE, sql -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be a primary key");
        verifyNoInteractions(connection);
    }

    @JoinTable(value = "too_long_primary", autoSchema = true)
    static class TooLongPrimary {
        @JoinMeta(primaryKey = true)
        @JoinField(textLength = 129)
        String id;
    }

    @JoinTable(value = "large_text_primary", autoSchema = true)
    static class LargeTextPrimary {
        @JoinMeta(primaryKey = true)
        @JoinField(textType = TextTypeEnum.text)
        String id;
    }

    @JoinTable(value = "preview_user", autoSchema = true)
    @JoinIndex(fields = "name")
    static class PreviewUser {
        @JoinMeta(primaryKey = true)
        String id;
        @JoinField(description = "Display name")
        String name;
    }
}

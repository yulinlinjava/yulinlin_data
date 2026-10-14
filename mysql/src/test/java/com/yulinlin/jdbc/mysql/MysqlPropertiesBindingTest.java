package com.yulinlin.jdbc.mysql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlPropertiesBindingTest {

    @Test
    void bindsSqlLoggingOptionsFromTheMysqlNamespace() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "yulinlin.mysql.log", "true",
                "yulinlin.mysql.log-params", "true",
                "yulinlin.mysql.max-param-length", "96",
                "yulinlin.mysql.slow-sql-threshold", "750ms"));

        MysqlProperties properties = new Binder(source)
                .bind("yulinlin.mysql", Bindable.of(MysqlProperties.class))
                .orElseThrow(() -> new AssertionError("MySQL properties were not bound"));

        assertThat(properties.isLog()).isTrue();
        assertThat(properties.isLogParams()).isTrue();
        assertThat(properties.getMaxParamLength()).isEqualTo(96);
        assertThat(properties.getSlowSqlThreshold()).isEqualTo(Duration.ofMillis(750));
    }
}

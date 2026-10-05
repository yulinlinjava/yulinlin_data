package com.yulinlin.jdbc.sqlite;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** File-only applications do not require Boot's separate server/embedded JDBC DataSource. */
public final class SqliteOnlyAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {
    private Environment environment;

    @Override public void setEnvironment(Environment environment) { this.environment = environment; }

    @Override public boolean[] match(String[] autoConfigurations, AutoConfigurationMetadata metadata) {
        boolean fileOnly = environment != null
                && !StringUtils.hasText(environment.getProperty("spring.datasource.url"))
                && !StringUtils.hasText(environment.getProperty("spring.datasource.jndi-name"));
        boolean[] matches = new boolean[autoConfigurations.length];
        for (int i = 0; i < matches.length; i++) {
            matches[i] = !fileOnly || !"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration".equals(autoConfigurations[i]);
        }
        return matches;
    }
}

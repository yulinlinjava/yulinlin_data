package com.yulinlin.jdbc.h2;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** A local-only application must not expose Boot's implicit embedded H2 DataSource bean. */
public final class H2OnlyAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {
    private Environment environment;

    @Override public void setEnvironment(Environment environment) { this.environment = environment; }

    @Override public boolean[] match(String[] autoConfigurations, AutoConfigurationMetadata metadata) {
        boolean localOnly = environment != null
                && !StringUtils.hasText(environment.getProperty("spring.datasource.url"))
                && !StringUtils.hasText(environment.getProperty("spring.datasource.jndi-name"));
        boolean[] matches = new boolean[autoConfigurations.length];
        for (int index = 0; index < matches.length; index++) {
            matches[index] = !localOnly
                    || !"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
                    .equals(autoConfigurations[index]);
        }
        return matches;
    }
}

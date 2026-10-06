package com.yulinlin.jdbc.h2;

import com.yulinlin.jdbc.DataJdbcApplication;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(after = {DataSourceAutoConfiguration.class, DataJdbcApplication.class})
@EnableConfigurationProperties(H2Properties.class)
public class H2AutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(H2Database.class)
    public H2Database h2Database(H2Properties properties, Environment environment) {
        if ("primary".equals(properties.getGroup()) && (environment.containsProperty("spring.datasource.url")
                || environment.containsProperty("spring.datasource.jndi-name"))) {
            throw new IllegalArgumentException("With a main DataSource, set yulinlin.h2.group=h2 (not primary)");
        }
        return new H2Database(properties);
    }

    @Bean("h2SessionFactory")
    @ConditionalOnMissingBean(name = "h2SessionFactory")
    public JdbcSessionFactory h2SessionFactory() {
        return new JdbcSessionFactory("jdbc:h2:", H2Session::new);
    }

    @Bean("h2Session")
    @ConditionalOnMissingBean(name = "h2Session")
    public JdbcSession h2Session(@Qualifier("h2SessionFactory") JdbcSessionFactory factory,
                                 H2Properties properties, H2Database database) {
        H2Session session = (H2Session) factory.create(database.dataSource(), properties.getGroup());
        session.configure(properties, database.schemaDataSource());
        return session;
    }
}

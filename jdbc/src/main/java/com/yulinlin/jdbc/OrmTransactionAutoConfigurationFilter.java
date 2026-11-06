package com.yulinlin.jdbc;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

/** 避免 Boot 自动再为同一个 @Transactional 注册原生事务拦截器。 */
public final class OrmTransactionAutoConfigurationFilter implements AutoConfigurationImportFilter {
    @Override public boolean[] match(String[] classes, AutoConfigurationMetadata metadata) {
        boolean[] matches = new boolean[classes.length];
        for (int i = 0; i < classes.length; i++)
            matches[i] = !"org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration".equals(classes[i]);
        return matches;
    }
}

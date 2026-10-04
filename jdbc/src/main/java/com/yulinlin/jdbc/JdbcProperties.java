package com.yulinlin.jdbc;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties("yulinlin.datasource.jdbc")
public class JdbcProperties {

    @Value("${log:false}")
    private boolean log;



    @Value("${mapUnderscoreToCamelCase:true}")
    private boolean mapUnderscoreToCamelCase;

}

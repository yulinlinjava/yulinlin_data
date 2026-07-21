package com.yulinlin.elasticsearch;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties("yulinlin.datasource.elasticsearch")
public class ElasticSearchProperties {

    @Value("${log:false}")
    private boolean log;



    @Value("${mapUnderscoreToCamelCase:true}")
    private boolean mapUnderscoreToCamelCase;


}

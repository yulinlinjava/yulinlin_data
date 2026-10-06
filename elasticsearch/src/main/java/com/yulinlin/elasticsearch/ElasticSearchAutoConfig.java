
package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yulinlin.data.core.schema.SchemaEntityScanner;
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.elasticsearch.log.EsLogPrint;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import com.yulinlin.elasticsearch.session.ElasticsearchSession;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchAutoConfig {
    @Bean
    public EsLogPrint esLogPrint(){
        return new EsLogPrint();
    }

    @Bean
    public ElasticSearchFactory elasticSearchFactory(ElasticSearchProperties properties){
        return new ElasticSearchFactory(new ElasticSearchParseManager(properties.getHighlight()));
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ElasticsearchClient.class)
    public ElasticsearchClient restClient(ElasticSearchProperties properties) {
        // Elasticsearch Java Client 9.x uses Rest5Client through this official factory.
        return ElasticsearchClient.of(builder -> builder.host(properties.getUrl()));
    }



    @ConditionalOnMissingBean
    @Bean
    public ElasticsearchSession elasticSearchSession(
            ElasticSearchFactory factory,
            ElasticsearchClient restClient,
            ElasticSearchProperties properties){
        ElasticsearchSession session = factory.create(restClient,"elasticsearch");
        if (properties.getSchemaMode() != SchemaMode.NONE) {
            session.initializeSchema(SchemaEntityScanner.scan(properties.getSchemaPackages()));
        }
        return session;
    }

}



package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.yulinlin.elasticsearch.log.EsLogPrint;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import com.yulinlin.elasticsearch.session.ElasticsearchSession;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchAutoConfig {
    @Bean
    public EsLogPrint esLogPrint(){
        return new EsLogPrint();
    }

    @Bean
    public ElasticSearchFactory elasticSearchFactory(){


        
        return new ElasticSearchFactory(new ElasticSearchParseManager());
    }

    @Bean
    public ElasticsearchClient restClient() {

        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200)).build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        ElasticsearchClient client = new ElasticsearchClient(transport);


        return client;
    }



    @ConditionalOnMissingBean
    @Bean
    public ElasticsearchSession elasticSearchSession(
            ElasticSearchFactory factory,
            ElasticsearchClient restClient){
        return factory.create(restClient,"elasticsearch");
    }

}


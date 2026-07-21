package com.yulinlin.mongodb;

import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.mongodb.log.MongoLogPrint;
import com.yulinlin.mongodb.parse.MongoParseManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@ComponentScan("com.yulinlin.mongodb")
@Configuration
@EnableConfigurationProperties(MongoProperties.class)

public class MongoDbAutoConfig {

    @Bean
    public MongoLogPrint mongoLogPrint(){
        return new MongoLogPrint();
    }

    @Bean
    public MongoFactory mongoFactory(){


        MongoFactory mongoFactory = new MongoFactory(new MongoParseManager());
        return mongoFactory;
    }
    
    @Bean
    public EntitySession session(MongoTemplate client, MongoFactory factory){
        return factory.create(client.getDb(),"mongo");
    }

}

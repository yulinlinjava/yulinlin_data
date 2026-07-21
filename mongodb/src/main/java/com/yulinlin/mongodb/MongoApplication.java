package com.yulinlin.mongodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.MongoTransactionManager;


@SpringBootApplication
public class MongoApplication {




    public static void main(String[] args) {

        SpringApplication.run(MongoApplication.class, args);
    }

}

package com.yulinlin.mongodb;

import com.yulinlin.data.core.session.EntitySessionProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.mongodb")
public class MongoProperties extends EntitySessionProperties {
}

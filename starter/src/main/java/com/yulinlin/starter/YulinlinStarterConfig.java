package com.yulinlin.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.lang.json.YulinlinSimpModule;
import com.yulinlin.data.lang.json.JsonUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@AutoConfiguration(before = JacksonAutoConfiguration.class)
public class YulinlinStarterConfig implements WebMvcConfigurer {


    @Primary
    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder){
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        objectMapper.registerModule(new YulinlinSimpModule());
        JsonUtil.setMapper(objectMapper);
        return objectMapper;
    }


}

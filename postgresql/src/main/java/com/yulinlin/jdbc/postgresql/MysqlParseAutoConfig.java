package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
public class MysqlParseAutoConfig {


    @ConditionalOnMissingBean
    @Bean
    public JdbcSessionFactory mysqlSessionFactory() {


        JdbcSessionFactory factory = new JdbcSessionFactory(new MysqlParseManager());

        return factory;
    }


    @Bean("jdbcSession")
    public JdbcSession jdbcSession(
            DataSource dataSource,JdbcSessionFactory jdbcSessionFactory
    ){
        JdbcSession sqlSession = jdbcSessionFactory.create(dataSource,"primary");
        return  sqlSession;
    }


}

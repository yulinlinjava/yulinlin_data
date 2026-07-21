package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.wrapper.IWrapperFactory;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
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

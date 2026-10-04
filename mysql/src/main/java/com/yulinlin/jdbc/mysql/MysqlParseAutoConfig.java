package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.wrapper.IWrapperFactory;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class MysqlParseAutoConfig {


    @ConditionalOnMissingBean(name = "mysqlSessionFactory")
    @Bean
    public JdbcSessionFactory mysqlSessionFactory() {


        JdbcSessionFactory factory = new JdbcSessionFactory(new MysqlParseManager());

        return factory;
    }


    @Bean("jdbcSession")
    @ConditionalOnMissingBean(name = "jdbcSession")
    @ConditionalOnSingleCandidate(DataSource.class)
    public JdbcSession jdbcSession(
            DataSource dataSource,
            @org.springframework.beans.factory.annotation.Qualifier("mysqlSessionFactory") JdbcSessionFactory jdbcSessionFactory
    ){
        JdbcSession sqlSession = jdbcSessionFactory.create(dataSource,"primary");
        return  sqlSession;
    }


}

package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.SessionFactory;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;

public class JdbcSessionFactory implements SessionFactory<DataSource> {

    private IParseManager parseManager;

    @Autowired
    IFilterManager filterManager;
    @Autowired
    EntityProxyService entityProxyService;

    @Autowired
    private  LogManager logManager;


    @Autowired
    private JdbcCoderManager jdbcCoderManager;

    @Autowired
    private DbCache dbCacheManager;


    @Autowired
    JdbcProperties properties;


    public JdbcSessionFactory(IParseManager parseManager) {
        this.parseManager = parseManager;
    }



    public JdbcSession create(DataSource dataSource,String group){


        JdbcSession sqlSession =  new JdbcSession(dataSource);
        sqlSession.setCacheManager(dbCacheManager);
        sqlSession.setProperties(properties);
        sqlSession.setCoderManager(jdbcCoderManager);
        sqlSession.setGroup(group);
        sqlSession.setLogManager(logManager);
        sqlSession.setParseManager(parseManager);
        sqlSession.setParseManager(parseManager);

        sqlSession.setFilterManager(filterManager);
        sqlSession.setProxyService(entityProxyService);
        return sqlSession;
    }

    public JdbcSession create(DataSourceProperties dataSourceProperties, String group){
        DataSource dataSource =   dataSourceProperties.initializeDataSourceBuilder().build();
        return create(dataSource,group);
    }


}

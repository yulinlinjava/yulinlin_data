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

    private final IParseManager parseManager;
    private final String jdbcUrlPrefix;
    private final java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator;

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
        this(parseManager, "jdbc:mysql:", JdbcSession::new);
    }

    public JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix) {
        this(parseManager, jdbcUrlPrefix, JdbcSession::new);
    }

    /** Create the concrete session, which installs its parsers and driver-specific value handling. */
    public JdbcSessionFactory(String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator) {
        this(null, jdbcUrlPrefix, sessionCreator);
    }

    private JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator) {
        this.parseManager = parseManager;
        this.jdbcUrlPrefix = java.util.Objects.requireNonNull(jdbcUrlPrefix, "jdbcUrlPrefix");
        this.sessionCreator = java.util.Objects.requireNonNull(sessionCreator, "sessionCreator");
    }

    public boolean supportsJdbcUrl(String url) {
        return url != null && url.startsWith(jdbcUrlPrefix);
    }



    public JdbcSession create(DataSource dataSource,String group){


        JdbcSession sqlSession = newSession(dataSource);
        sqlSession.setCacheManager(dbCacheManager);
        sqlSession.setProperties(properties);
        sqlSession.setCoderManager(jdbcCoderManager);
        sqlSession.setGroup(group);
        sqlSession.setLogManager(logManager);
        if (parseManager != null) sqlSession.setParseManager(parseManager);

        sqlSession.setFilterManager(filterManager);
        sqlSession.setProxyService(entityProxyService);
        return sqlSession;
    }

    protected JdbcSession newSession(DataSource dataSource) {
        return sessionCreator.apply(dataSource);
    }

    public JdbcSession create(DataSourceProperties dataSourceProperties, String group){
        DataSource dataSource =   dataSourceProperties.initializeDataSourceBuilder().build();
        return create(dataSource,group);
    }


}

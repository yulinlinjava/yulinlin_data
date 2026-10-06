package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.SessionFactory;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;

public class JdbcSessionFactory implements SessionFactory<DataSource> {

    private final IParseManager parseManager;
    private final String jdbcUrlPrefix;
    private final java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator;
    private final JdbcSessionProperties properties;

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


    public JdbcSessionFactory(IParseManager parseManager) {
        this(parseManager, "jdbc:mysql:", JdbcSession::new, new JdbcSessionProperties());
    }

    public JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix) {
        this(parseManager, jdbcUrlPrefix, JdbcSession::new, new JdbcSessionProperties());
    }

    public JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix, JdbcSessionProperties properties) {
        this(parseManager, jdbcUrlPrefix, JdbcSession::new, properties);
    }

    /** Create the concrete session, which installs its parsers and driver-specific value handling. */
    public JdbcSessionFactory(String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator) {
        this(null, jdbcUrlPrefix, sessionCreator, new JdbcSessionProperties());
    }

    public JdbcSessionFactory(String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator,
            JdbcSessionProperties properties) {
        this(null, jdbcUrlPrefix, sessionCreator, properties);
    }

    private JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator,
            JdbcSessionProperties properties) {
        this.parseManager = parseManager;
        this.jdbcUrlPrefix = java.util.Objects.requireNonNull(jdbcUrlPrefix, "jdbcUrlPrefix");
        this.sessionCreator = java.util.Objects.requireNonNull(sessionCreator, "sessionCreator");
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
    }

    public boolean supportsJdbcUrl(String url) {
        return url != null && url.startsWith(jdbcUrlPrefix);
    }



    public JdbcSession create(DataSource dataSource,String group){
        return create(dataSource, group, properties);
    }

    public JdbcSession create(DataSource dataSource, String group, JdbcSessionProperties properties){


        JdbcSession sqlSession = newSession(dataSource);
        sqlSession.setCacheManager(dbCacheManager);
        sqlSession.setProperties(java.util.Objects.requireNonNull(properties, "properties"));
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

    public JdbcSession create(DataSourceProperties dataSourceProperties, String group,
                              JdbcSessionProperties properties){
        DataSource dataSource = dataSourceProperties.initializeDataSourceBuilder().build();
        return create(dataSource, group, properties);
    }

    /** @deprecated Use the JdbcSessionProperties overload. */
    @Deprecated
    public JdbcSession create(DataSource dataSource, String group, JdbcProperties properties) {
        return create(dataSource, group, (JdbcSessionProperties) properties);
    }

    /** @deprecated Use the JdbcSessionProperties overload. */
    @Deprecated
    public JdbcSession create(DataSourceProperties dataSourceProperties, String group,
                              JdbcProperties properties) {
        return create(dataSourceProperties, group, (JdbcSessionProperties) properties);
    }


}

package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.CacheRegistry;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.session.SessionExecutionListener;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.SessionFactory;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;

public class JdbcSessionFactory implements SessionFactory<DataSource> {

    private final IParseManager parseManager;
    private final String jdbcUrlPrefix;
    private final java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator;
    private final JdbcSessionProperties properties;
    private final ICoderManager coderManager;

    private QueryCache queryCache = NoOpQueryCache.INSTANCE;

    @Autowired
    EntityProxyService entityProxyService;

    private java.util.List<SessionExecutionListener> executionListeners = java.util.List.of();

    @Autowired
    void setExecutionListeners(ObjectProvider<SessionExecutionListener> listeners) {
        this.executionListeners = listeners.orderedStream().toList();
    }


    @Autowired
    void setQueryCaches(CacheRegistry caches) {
        this.queryCache = caches.defaultCache();
    }

    public JdbcSessionFactory(IParseManager parseManager) {
        this(parseManager, "jdbc:mysql:", JdbcSession::new, new JdbcSessionProperties());
    }

    public JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix) {
        this(parseManager, jdbcUrlPrefix, JdbcSession::new, new JdbcSessionProperties());
    }

    public JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix, JdbcSessionProperties properties) {
        this(parseManager, jdbcUrlPrefix, JdbcSession::new, properties);
    }

    public JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix, JdbcSessionProperties properties,
            ICoderManager coderManager) {
        this(parseManager, jdbcUrlPrefix, JdbcSession::new, properties, coderManager);
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

    /** 直接使用数据库自己的编解码管理器，不传编码器列表、不依赖全局管理器 Bean。 */
    public JdbcSessionFactory(String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator,
            JdbcSessionProperties properties, ICoderManager coderManager) {
        this(null, jdbcUrlPrefix, sessionCreator, properties, coderManager);
    }

    private JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator,
            JdbcSessionProperties properties) {
        this(parseManager, jdbcUrlPrefix, sessionCreator, properties, new JdbcCoderManager());
    }

    private JdbcSessionFactory(IParseManager parseManager, String jdbcUrlPrefix,
            java.util.function.Function<DataSource, ? extends JdbcSession> sessionCreator,
            JdbcSessionProperties properties, ICoderManager coderManager) {
        this.parseManager = parseManager;
        this.jdbcUrlPrefix = java.util.Objects.requireNonNull(jdbcUrlPrefix, "jdbcUrlPrefix");
        this.sessionCreator = java.util.Objects.requireNonNull(sessionCreator, "sessionCreator");
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
        this.coderManager = java.util.Objects.requireNonNull(coderManager, "coderManager");
    }

    public boolean supportsJdbcUrl(String url) {
        return url != null && url.startsWith(jdbcUrlPrefix);
    }



    /** 返回已完成依赖初始化的节点；业务执行仍统一通过 RouteSession。 */
    @Override
    public JdbcSession create(DataSource dataSource,String group){
        return create(dataSource, group, properties);
    }

    public JdbcSession create(DataSource dataSource, String group, JdbcSessionProperties properties){


        JdbcSession sqlSession = newSession(dataSource);
        sqlSession.setQueryCache(queryCache);
        sqlSession.setProperties(java.util.Objects.requireNonNull(properties, "properties"));
        sqlSession.setCoderManager(coderManager);
        sqlSession.setGroup(group);
        sqlSession.setExecutionListeners(executionListeners);
        if (parseManager != null) sqlSession.setParseManager(parseManager);

        sqlSession.setProxyService(entityProxyService);
        return sqlSession;
    }

    /** 仅创建原始节点，公共组件由 create(...) 统一初始化。 */
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

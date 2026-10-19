package com.yulinlin.data.core.session;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.mongodb.client.MongoDatabase;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.elasticsearch.ElasticSearchFactory;
import com.yulinlin.elasticsearch.ElasticSearchProperties;
import com.yulinlin.elasticsearch.coder.ElasticCoderManager;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import com.yulinlin.jdbc.sql.SqlParseManager;
import com.yulinlin.mongodb.MongoFactory;
import com.yulinlin.mongodb.MongoProperties;
import com.yulinlin.mongodb.coder.MongoCoderManager;
import com.yulinlin.mongodb.parse.MongoParseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 只使用容器装配和客户端桩，不连接数据库，也不加载 YAML。 */
class SessionFactoryInitializationTest {
    private final List<AbstractSession> created = new ArrayList<>();

    @AfterEach void closeExecutors() {
        created.forEach(session -> session.getThreadPoolExecutor().shutdownNow());
    }

    @Test void factoriesInitializeAllComponentsBeforeRouteRegistration() {
        QueryCache cache = mock(QueryCache.class);
        try (var context = context(cache)) {
            List<AbstractSession> sessions = createSessions(context);
            assertThat(sessions).extracting(AbstractSession::group)
                    .containsExactly("mysql", "elasticsearch", "mongo");
            for (AbstractSession session : sessions) {
                assertThat(session.getCacheManager()).isSameAs(cache);
                assertThat(session.getParseManager()).isNotNull();
                assertThat(session.getProxyService()).isSameAs(context.getBean(EntityProxyService.class));
                assertThat(session.getLogManager()).isSameAs(context.getBean(LogManager.class));
            }
            assertThat(sessions.get(0).getCoderManager()).isSameAs(context.getBean(JdbcCoderManager.class));
            assertThat(((JdbcSession) sessions.get(0)).getProperties())
                    .isSameAs(context.getBean(JdbcSessionProperties.class));
            assertThat(sessions.get(1).getCoderManager()).isInstanceOf(ElasticCoderManager.class);
            assertThat(sessions.get(1).getSessionProperties())
                    .isSameAs(context.getBean(ElasticSearchProperties.class));
            assertThat(sessions.get(2).getCoderManager()).isInstanceOf(MongoCoderManager.class);
            assertThat(sessions.get(2).getSessionProperties())
                    .isSameAs(context.getBean(MongoProperties.class));

            RouteSession route = RouteSession.builder().build();
            route.setLoadBalance(new RandomLoadBalance());
            route.setQueryCache(cache);
            for (AbstractSession initialized : sessions) {
                AbstractSession session = spy(initialized);
                route.registerSession(session);
                assertThat(route.session(session.group())).isSameAs(session);
                verify(session, never()).setQueryCache(any());
                verify(session, never()).setCoderManager(any());
                verify(session, never()).setParseManager(any());
                verify(session, never()).setProxyService(any());
                verify(session, never()).setLogManager(any());
                assertThat(session.getCacheManager()).isSameAs(cache);
            }
        }
    }

    @Test void factoriesUseNoOpWhenNoCacheProviderIsInstalled() {
        try (var context = context(null)) {
            for (AbstractSession session : createSessions(context)) {
                assertThat(session.getCacheManager()).isSameAs(NoOpQueryCache.INSTANCE);
                assertThat(session.getProxyService()).isNotNull();
            }
        }
    }

    @Test void executionInterfaceDoesNotExposeCacheConfiguration() {
        assertThat(Arrays.stream(EntitySession.class.getMethods()).map(Method::getName).toList())
                .doesNotContain("setQueryCache");
    }

    private AnnotationConfigApplicationContext context(QueryCache cache) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(EntityProxyService.class, () -> mock(EntityProxyService.class));
        context.registerBean(LogManager.class, LogManager::new);
        context.registerBean(JdbcCoderManager.class, JdbcCoderManager::new);
        context.registerBean(JdbcSessionProperties.class, JdbcSessionProperties::new);
        context.registerBean(ElasticSearchProperties.class, ElasticSearchProperties::new);
        context.registerBean(MongoProperties.class, MongoProperties::new);
        context.registerBean(JdbcSessionFactory.class, () -> new JdbcSessionFactory(
                new SqlParseManager(), "jdbc:mysql:", context.getBean(JdbcSessionProperties.class)));
        context.registerBean(ElasticSearchFactory.class,
                () -> new ElasticSearchFactory(new ElasticSearchParseManager()));
        context.registerBean(MongoFactory.class, () -> new MongoFactory(new MongoParseManager()));
        if (cache != null) context.registerBean(QueryCache.class, () -> cache);
        context.refresh();
        return context;
    }

    private List<AbstractSession> createSessions(AnnotationConfigApplicationContext context) {
        DataSource dataSource = mock(DataSource.class);
        ElasticsearchClient elasticsearch = mock(ElasticsearchClient.class);
        MongoDatabase mongo = mock(MongoDatabase.class);
        List<AbstractSession> sessions = List.of(
                context.getBean(JdbcSessionFactory.class).create(dataSource, "mysql"),
                context.getBean(ElasticSearchFactory.class).create(elasticsearch, "elasticsearch"),
                context.getBean(MongoFactory.class).create(mongo, "mongo"));
        created.addAll(sessions);
        verifyNoInteractions(dataSource, elasticsearch, mongo);
        return sessions;
    }
}

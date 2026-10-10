package com.yulinlin.repository;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.aop.JoinSessionAop;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.repository.fixture.routing.RoutedRepository;
import com.yulinlin.repository.proxy.MethodParseManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.MapPropertySource;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryJoinSessionTest {

    @Test
    void repositoryInterfaceAndMethodAnnotationsSelectAndRestoreRoutes() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "yulinlin.repository.scan-packages[0]",
                    "com.yulinlin.repository.fixture.routing")));
            context.register(AopConfiguration.class, RepositoryAutoConfig.class);
            context.refresh();

            RoutedRepository repository = context.getBean(RoutedRepository.class);
            assertThat(repository.interfaceRoute()).isEqualTo("mysql:master");
            assertThat(repository.methodRoute()).isEqualTo("postgresql:master");
            assertThat(repository.slaveRoute()).isEqualTo("mysql:slave");
            assertThat(repository.defaultRoute()).isEqualTo("mysql");
            assertThat(repository.getEntityClass()).isEqualTo(RoutedRepository.RoutedEntity.class);
            assertThat(SessionUtil.route().session().group()).isEqualTo("local");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopConfiguration {

        @Bean
        JoinSessionAop joinSessionAop() {
            return new JoinSessionAop();
        }

        @Bean
        MethodParseManager routingMethodParseManager() {
            return new MethodParseManager() {
                @Override
                public Object apply(String name, Object[] args, Method method, Object proxy) {
                    EntitySession selected = SessionUtil.route().session();
                    return selected.group() + ":" + selected.cluster().name();
                }
            };
        }

        @Bean
        RouteSession routeSession() {
            RandomLoadBalance balance = new RandomLoadBalance();
            balance.setDefaultGroup("local");
            balance.register(session("local", JoinCluster.master));
            balance.register(session("mysql", JoinCluster.master));
            balance.register(session("mysql", JoinCluster.slave));
            balance.register(session("postgresql", JoinCluster.master));
            RouteSession route = RouteSession.builder().build();
            route.setLoadBalance(balance);
            return route;
        }

        @Bean
        SessionUtil sessionUtil(RouteSession routeSession) {
            return new SessionUtil(routeSession);
        }

        private static EntitySession session(String group, JoinCluster cluster) {
            EntitySession session = mock(EntitySession.class);
            when(session.group()).thenReturn(group);
            when(session.cluster()).thenReturn(cluster);
            when(session.weight()).thenReturn(1);
            when(session.ping()).thenReturn(true);
            return session;
        }
    }
}

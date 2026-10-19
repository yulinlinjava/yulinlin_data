package com.yulinlin.admin.support;

import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;

import java.util.concurrent.Callable;

import static org.mockito.Mockito.*;

/** 执行器专项用例仍通过 RouteSession 开启业务事务，不依赖 Spring 或全局 SessionUtil。 */
public final class TransactionTestSupport {
    private TransactionTestSupport() { }

    public static RouteSession route(EntitySession session) {
        RouteSession route = RouteSession.builder().build();
        LoadBalance balance = mock(LoadBalance.class);
        when(balance.defaultGroup()).thenReturn(session.group());
        when(balance.loadBalance(session.group(), session.cluster())).thenReturn(session);
        route.setLoadBalance(balance);
        return route;
    }

    public static <T> T inRoute(EntitySession session, Callable<T> action) {
        RouteSession route = route(session);
        return route.transaction(() -> {
            route.session();
            return action.call();
        });
    }
}

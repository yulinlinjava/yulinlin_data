package com.yulinlin.security.risk;

import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.session.JdbcSession;
import org.junit.jupiter.api.AfterEach;
import java.util.ArrayList;
import java.util.List;

abstract class OrmTestSupport {
    private final RouteSession previous = SessionUtil.route();
    private final List<JdbcSession> sessions = new ArrayList<>();

    protected RouteSession install(JdbcSession session) {
        sessions.add(session);
        session.setGroup("risk-test");
        session.setProxyService(EntityProxyService.newInstance());
        var route = RouteSession.builder().proxyService(EntityProxyService.newInstance()).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.registerSession(session);
        route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
        new SessionUtil(route);
        return route;
    }

    @AfterEach void closeOrm() {
        sessions.forEach(session -> session.getThreadPoolExecutor().shutdownNow());
        new SessionUtil(previous);
    }
}

package com.yulinlin.repository.fixture.routing;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.anno.JoinRepository;
import com.yulinlin.repository.dao.BaseRepository;

@JoinRepository
@JoinSession("mysql")
public interface RoutedRepository extends BaseRepository<RoutedRepository.RoutedEntity> {

    String interfaceRoute();

    @JoinSession("postgresql")
    String methodRoute();

    @JoinSession(value = "mysql", cluster = JoinCluster.slave)
    String slaveRoute();

    default String defaultRoute() {
        return SessionUtil.nowSession();
    }

    class RoutedEntity {
    }
}

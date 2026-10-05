package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterSessionDefaultGroupTest {
    private EntitySession node(String group) {
        var session = mock(EntitySession.class);
        when(session.group()).thenReturn(group);
        when(session.cluster()).thenReturn(JoinCluster.master);
        when(session.weight()).thenReturn(1);
        when(session.ping()).thenReturn(true);
        return session;
    }

    private RegisterSession route(RandomLoadBalance balance, EntitySession... sessions) {
        var route = new RegisterSession();
        route.setLoadBalance(balance);
        for (var session : sessions) route.registerSession(session);
        return route;
    }

    @Test void aSingleNamedGroupWorksWithoutExplicitSelector() {
        for (String group : List.of("mysql", "postgresql", "sqlite", "local")) {
            var session = node(group);
            assertThat(route(new RandomLoadBalance(), session).session()).isSameAs(session);
        }
    }

    @Test void configuredDefaultIsUsedWithMultipleGroups() {
        var mysql = node("mysql"); var sqlite = node("sqlite");
        var balance = new RandomLoadBalance();
        balance.setDefaultGroup("mysql");
        assertThat(route(balance, mysql, sqlite).session()).isSameAs(mysql);
    }

    @Test void primaryIsAnOrdinaryGroupAndMustBeConfiguredWhenMultipleGroupsExist() {
        var balance = new RandomLoadBalance();
        var primary = node("primary");
        var route = route(balance, primary, node("mysql"));
        assertThatThrownBy(route::session).isInstanceOf(NoticeException.class);
        balance.setDefaultGroup("primary");
        assertThat(route.session()).isSameAs(primary);
    }

    @Test void multipleGroupsWithoutDefaultRequireExplicitSelector() {
        var route = route(new RandomLoadBalance(), node("mysql"), node("postgresql"));
        assertThatThrownBy(route::session).isInstanceOf(NoticeException.class)
                .hasMessageContaining("yulinlin.datasource.default-group");
    }

    @Test void explicitSelectorWinsOverConfiguredDefault() {
        var balance = new RandomLoadBalance();
        balance.setDefaultGroup("postgresql");
        var mysql = node("mysql");
        assertThat(route(balance, mysql, node("postgresql")).session("mysql")).isSameAs(mysql);
    }

    @Test void activeRouteContextWinsOverDefaultSelection() {
        var route = route(new RandomLoadBalance(), node("mysql"), node("postgresql"));
        var active = node("context");
        route.pushSession(active);
        try { assertThat(route.session()).isSameAs(active); }
        finally { route.popSession(); }
    }

    @Test void changingDefaultTakesEffectForSubsequentUnscopedRequests() {
        var balance = new RandomLoadBalance();
        var mysql = node("mysql"); var pg = node("postgresql");
        var route = route(balance, mysql, pg);
        balance.setDefaultGroup("mysql");
        assertThat(route.session()).isSameAs(mysql);
        balance.setDefaultGroup("postgresql");
        assertThat(route.session()).isSameAs(pg);
    }

    @Test void outsideTransactionSelectionsAreNotCachedIndefinitely() {
        var balance = new FixedDrawBalance();
        var first = node("mysql"); var second = node("mysql");
        var route = route(balance, first, second);
        balance.offset = 0;
        assertThat(route.session()).isSameAs(first);
        balance.offset = 1;
        assertThat(route.session()).isSameAs(second);
    }

    @Test void transactionRetainsNodeAffinityUntilItEnds() {
        var balance = new FixedDrawBalance();
        var first = node("mysql"); var second = node("mysql");
        var route = route(balance, first, second);
        balance.offset = 0;
        route.startTransaction();
        try {
            assertThat(route.session()).isSameAs(first);
            balance.offset = 1;
            assertThat(route.session()).isSameAs(first);
        } finally { route.rollbackTransaction(); }
        assertThat(route.session()).isSameAs(second);
    }

    @Test void healthRefreshAffectsRequestsOutsideTransaction() {
        var balance = new RandomLoadBalance();
        var session = node("mysql");
        var route = route(balance, session);
        assertThat(route.session()).isSameAs(session);
        when(session.ping()).thenReturn(false);
        balance.ping();
        assertThatThrownBy(route::session).isInstanceOf(NoticeException.class);
    }

    private static class FixedDrawBalance extends RandomLoadBalance {
        long offset;
        @Override protected long randomWeight(long bound) { return offset; }
    }
}

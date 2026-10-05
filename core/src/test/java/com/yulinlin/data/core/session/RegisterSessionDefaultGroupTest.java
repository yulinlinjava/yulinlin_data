package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterSessionDefaultGroupTest {
    private String previousMaster;

    @BeforeEach void preserveConfiguredMaster() {
        previousMaster = RegisterSession.master;
        RegisterSession.master = "primary";
    }

    @AfterEach void restoreConfiguredMaster() {
        RegisterSession.master = previousMaster;
    }

    private RegisterSession route(LoadBalance balance, Set<String> groups) {
        when(balance.loadBalanceList()).thenReturn(groups);
        var route = new RegisterSession();
        route.setLoadBalance(balance);
        return route;
    }

    @Test void aSingleNamedGroupWorksWithoutExplicitSelector() {
        for (String group : List.of("mysql", "postgresql", "sqlite", "local")) {
            var balance = mock(LoadBalance.class);
            var session = mock(EntitySession.class);
            when(balance.<EntitySession>loadBalance(group, JoinCluster.master)).thenReturn(session);
            assertThat(route(balance, Set.of(group)).session()).isSameAs(session);
        }
        assertThat(RegisterSession.master).isEqualTo("primary"); // Selection is per route, not a global mutation.
    }

    @Test void legacyPrimaryGroupIsStillPreferredWhenPresent() {
        var balance = mock(LoadBalance.class);
        var primary = mock(EntitySession.class);
        when(balance.<EntitySession>loadBalance("primary", JoinCluster.master)).thenReturn(primary);
        assertThat(route(balance, Set.of("primary", "mysql")).session()).isSameAs(primary);
    }

    @Test void multipleNamedGroupsRequireExplicitSelector() {
        var balance = mock(LoadBalance.class);
        var route = route(balance, Set.of("mysql", "postgresql"));
        assertThatThrownBy(route::session).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("specify a session group explicitly");
        verify(balance, never()).loadBalance(anyString(), any());
    }

    @Test void explicitSelectorWorksWithMultipleGroups() {
        var balance = mock(LoadBalance.class);
        var mysql = mock(EntitySession.class);
        when(balance.<EntitySession>loadBalance("mysql", JoinCluster.master)).thenReturn(mysql);
        assertThat(route(balance, Set.of("mysql", "postgresql")).session("mysql")).isSameAs(mysql);
    }

    @Test void activeRouteContextWinsOverDefaultSelection() {
        var balance = mock(LoadBalance.class);
        var route = route(balance, Set.of("mysql", "postgresql"));
        var active = mock(EntitySession.class);
        route.pushSession(active);
        try {
            assertThat(route.session()).isSameAs(active);
        } finally {
            route.popSession();
        }
        verify(balance, never()).loadBalance(anyString(), any());
    }

    @Test void explicitlyConfiguredMissingMasterDoesNotFallBackToAnotherGroup() {
        RegisterSession.master = "reporting";
        var balance = mock(LoadBalance.class);
        var route = route(balance, Set.of("mysql"));
        assertThatThrownBy(route::session).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reporting");
    }

    @Test void noRegisteredGroupProducesClearFailure() {
        var route = route(mock(LoadBalance.class), Set.of());
        assertThatThrownBy(route::session).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No session group has been registered");
    }

    @Test void registrationDoesNotChangeGlobalDefaultEvenWhenItIsUnset() {
        RegisterSession.master = null;
        var balance = mock(LoadBalance.class);
        var route = route(balance, Set.of("mysql"));
        var session = mock(EntitySession.class);
        when(session.group()).thenReturn("mysql");
        when(balance.<EntitySession>loadBalance("mysql", JoinCluster.master)).thenReturn(session);
        route.registerSession(session);
        assertThat(RegisterSession.master).isNull();
        assertThat(route.session()).isSameAs(session);
    }
}
